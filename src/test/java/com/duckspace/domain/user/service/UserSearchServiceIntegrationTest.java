package com.duckspace.domain.user.service;

import com.duckspace.domain.user.dto.response.UserSearchResponse;
import com.duckspace.domain.user.entity.AuthProvider;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.domain.user.repository.UserSearchHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mockito로는 못 잡는 문제를 실제 트랜잭션 경계로 검증합니다.
 *
 * <p>실제로 {@link UserSearchHistoryWriter}의 삭제와 INSERT를 서로 다른 트랜잭션으로 나눠뒀다가,
 * REQUIRES_NEW 트랜잭션이 아직 커밋 안 된 바깥 트랜잭션의 삭제를 못 보고 유니크 제약에 걸려서
 * 이미 있던 항목을 다시 클릭하면 맨 위로 옮겨지는 게 아니라 통째로 사라지는 버그가 있었습니다.
 * Mockito 테스트는 writer를 목으로 대체해서 이 상호작용 자체를 검증하지 못했습니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserSearchServiceIntegrationTest {

    @Autowired
    private UserSearchService userSearchService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSearchHistoryRepository searchHistoryRepository;

    private Long searcherId;
    private Long targetId;

    @BeforeEach
    void setUp() {
        searcherId = userRepository.save(user("나")).getId();
        targetId = userRepository.save(user("검색대상")).getId();
    }

    @AfterEach
    void tearDown() {
        searchHistoryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private User user(String nickname) {
        return User.builder()
                .email(nickname + "-" + System.nanoTime() + "@duckspace.com")
                .nickname(nickname)
                .password("encoded")
                .authProvider(AuthProvider.LOCAL)
                .build();
    }

    @Test
    @DisplayName("이미 있는 항목을 다시 클릭해도 사라지지 않고 그대로 남아있다")
    void 이미_있는_항목_재클릭은_사라지지_않는다() {
        userSearchService.record(searcherId, targetId);
        userSearchService.record(searcherId, targetId);

        List<UserSearchResponse> history = userSearchService.getHistory(searcherId);

        assertThat(history).extracting(UserSearchResponse::userId).containsExactly(targetId);
        assertThat(searchHistoryRepository.countBySearcherId(searcherId)).isEqualTo(1);
    }

    @Test
    @DisplayName("재클릭하면 맨 위로 올라온다")
    void 재클릭하면_맨_위로_올라온다() {
        Long other = userRepository.save(user("다른대상")).getId();
        userSearchService.record(searcherId, targetId);
        userSearchService.record(searcherId, other);

        userSearchService.record(searcherId, targetId);   // targetId 재클릭

        List<UserSearchResponse> history = userSearchService.getHistory(searcherId);
        assertThat(history).extracting(UserSearchResponse::userId).containsExactly(targetId, other);
    }

    @Test
    @DisplayName("3개를 넘기면 가장 오래된 것부터 사라진다")
    void 세개_초과시_가장_오래된_것부터_삭제() {
        Long t2 = userRepository.save(user("대상2")).getId();
        Long t3 = userRepository.save(user("대상3")).getId();
        Long t4 = userRepository.save(user("대상4")).getId();

        userSearchService.record(searcherId, targetId);
        userSearchService.record(searcherId, t2);
        userSearchService.record(searcherId, t3);
        userSearchService.record(searcherId, t4);

        List<UserSearchResponse> history = userSearchService.getHistory(searcherId);
        assertThat(history).extracting(UserSearchResponse::userId).containsExactly(t4, t3, t2);
        assertThat(searchHistoryRepository.countBySearcherId(searcherId)).isEqualTo(3);
    }
}
