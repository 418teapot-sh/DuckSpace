package com.duckspace.domain.user.repository;

import com.duckspace.domain.user.entity.AuthProvider;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.entity.UserSearchHistory;
import com.duckspace.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 검색 내역(UserSearchHistory) 쿼리를 실제 MySQL로 확인합니다.
 * - findBySearcherIdOrderByIdDesc가 최근 클릭 순으로 나오는지 (fetch join으로 searchedUser도 채워지는지)
 * - 트리밍용 findIdsBySearcherIdOrderByIdDesc / deleteBySearcherIdAndIdNotIn 벌크 삭제
 * - 닉네임 검색(UserRepository.searchByNickname)이 대소문자 무시하고 부분일치하는지
 * - (searcher_id, searched_user_id) 유니크 제약이 실제로 DB에서 걸리는지
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class UserSearchHistoryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSearchHistoryRepository searchHistoryRepository;

    private User newUser(String nickname) {
        return userRepository.save(User.builder()
                .email(nickname + "-" + System.nanoTime() + "@duckspace.com")
                .nickname(nickname)
                .password("encoded")
                .authProvider(AuthProvider.LOCAL)
                .build());
    }

    @Test
    void 검색_내역은_최근_클릭_순으로_나오고_searchedUser도_바로_채워진다() {
        User searcher = newUser("나");
        User first = newUser("먼저클릭한사람");
        User second = newUser("나중에클릭한사람");

        searchHistoryRepository.save(UserSearchHistory.of(searcher, first));
        searchHistoryRepository.save(UserSearchHistory.of(searcher, second));
        entityManager.flush();
        entityManager.clear();

        List<UserSearchHistory> history = searchHistoryRepository
                .findBySearcherIdOrderByIdDesc(searcher.getId(), PageRequest.of(0, 3));

        assertThat(history).extracting(h -> h.getSearchedUser().getNickname())
                .containsExactly("나중에클릭한사람", "먼저클릭한사람");
    }

    @Test
    void 다른_사람의_내역은_섞여_나오지_않는다() {
        User searcher = newUser("나");
        User other = newUser("다른유저");
        User target = newUser("검색대상");

        searchHistoryRepository.save(UserSearchHistory.of(searcher, target));
        searchHistoryRepository.save(UserSearchHistory.of(other, target));
        entityManager.flush();
        entityManager.clear();

        assertThat(searchHistoryRepository.countBySearcherId(searcher.getId())).isEqualTo(1);
    }

    @Test
    void findIdsBySearcherIdOrderByIdDesc는_최신순으로_id만_돌려준다() {
        User searcher = newUser("나");
        UserSearchHistory oldest = searchHistoryRepository.save(UserSearchHistory.of(searcher, newUser("A")));
        UserSearchHistory newest = searchHistoryRepository.save(UserSearchHistory.of(searcher, newUser("B")));
        entityManager.flush();
        entityManager.clear();

        List<Long> ids = searchHistoryRepository.findIdsBySearcherIdOrderByIdDesc(searcher.getId(), PageRequest.of(0, 10));

        assertThat(ids).containsExactly(newest.getId(), oldest.getId());
    }

    @Test
    void deleteBySearcherIdAndIdNotIn는_keepIds에_없는_것만_한번에_지운다() {
        User searcher = newUser("나");
        User other = newUser("다른유저");
        UserSearchHistory keep = searchHistoryRepository.save(UserSearchHistory.of(searcher, newUser("남길것")));
        UserSearchHistory trim1 = searchHistoryRepository.save(UserSearchHistory.of(searcher, newUser("밀려날것1")));
        UserSearchHistory trim2 = searchHistoryRepository.save(UserSearchHistory.of(searcher, newUser("밀려날것2")));
        UserSearchHistory othersRow = searchHistoryRepository.save(UserSearchHistory.of(other, newUser("남의것")));
        entityManager.flush();
        entityManager.clear();

        searchHistoryRepository.deleteBySearcherIdAndIdNotIn(searcher.getId(), List.of(keep.getId()));
        entityManager.flush();
        entityManager.clear();

        assertThat(searchHistoryRepository.countBySearcherId(searcher.getId())).isEqualTo(1);
        assertThat(searchHistoryRepository.findById(keep.getId())).isPresent();
        assertThat(searchHistoryRepository.findById(trim1.getId())).isEmpty();
        assertThat(searchHistoryRepository.findById(trim2.getId())).isEmpty();
        assertThat(searchHistoryRepository.findById(othersRow.getId()))
                .as("다른 사람 내역은 keepIds와 무관하게 안 지워져야 합니다")
                .isPresent();
    }

    @Test
    void deleteAllBySearcherId는_본인_내역만_지운다() {
        User searcher = newUser("나");
        User other = newUser("다른유저");
        User target = newUser("검색대상");
        searchHistoryRepository.save(UserSearchHistory.of(searcher, target));
        searchHistoryRepository.save(UserSearchHistory.of(other, target));
        entityManager.flush();

        searchHistoryRepository.deleteAllBySearcherId(searcher.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(searchHistoryRepository.countBySearcherId(searcher.getId())).isZero();
        assertThat(searchHistoryRepository.countBySearcherId(other.getId())).isEqualTo(1);
    }

    @Test
    void 같은_조합을_두번_저장하면_유니크_제약에_걸린다() {
        User searcher = newUser("나");
        User target = newUser("검색대상");
        searchHistoryRepository.save(UserSearchHistory.of(searcher, target));
        entityManager.flush();

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            searchHistoryRepository.save(UserSearchHistory.of(searcher, target));
            entityManager.flush();
        });
    }

    @Test
    void searchByNickname은_대소문자_무시하고_부분일치한다() {
        newUser("DuckLover");
        newUser("치이카와덕후");
        newUser("상관없는유저");
        entityManager.flush();
        entityManager.clear();

        List<User> result = userRepository.searchByNickname("duck", PageRequest.of(0, 10));

        assertThat(result).extracting(User::getNickname).containsExactly("DuckLover");
    }
}
