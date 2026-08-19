package com.duckspace.domain.popup.repository;

import com.duckspace.domain.popup.entity.Popup;
import com.duckspace.domain.popup.entity.PopupLike;
import com.duckspace.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PR #55 리뷰에서 나온 두 가지를 실제 MySQL로 확인합니다.
 * - deletePopup 후 PopupLike 고아 행이 안 남는지 (deleteByPopupId)
 * - findLikedPopups join 쿼리가 최근 찜한 순으로 정확히 나오는지
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class PopupLikeRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PopupRepository popupRepository;

    @Autowired
    private PopupLikeRepository popupLikeRepository;

    private Popup newPopup(String title) {
        return popupRepository.save(Popup.builder()
                .title(title)
                .imageUrl("https://example.com/" + title + ".png")
                .description("설명")
                .location("성수동")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 10))
                .build());
    }

    @Test
    void deleteByPopupId로_지우면_해당_팝업의_찜_행이_전부_사라진다() {
        Popup popup = newPopup("치이카와");
        popupLikeRepository.save(new PopupLike(popup, 1L));
        popupLikeRepository.save(new PopupLike(popup, 2L));
        entityManager.flush();
        entityManager.clear();

        popupLikeRepository.deleteByPopupId(popup.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(popupLikeRepository.existsByPopupIdAndUserId(popup.getId(), 1L)).isFalse();
        assertThat(popupLikeRepository.existsByPopupIdAndUserId(popup.getId(), 2L)).isFalse();
    }

    @Test
    void findLikedPopups는_최근_찜한_순으로_팝업을_바로_돌려준다() {
        Popup first = newPopup("먼저찜한팝업");
        Popup second = newPopup("나중에찜한팝업");
        Long userId = 10L;

        popupLikeRepository.save(new PopupLike(first, userId));
        popupLikeRepository.save(new PopupLike(second, userId));
        // 다른 유저의 찜은 섞여 나오면 안 됩니다.
        popupLikeRepository.save(new PopupLike(first, 99L));
        entityManager.flush();
        entityManager.clear();

        List<Popup> liked = popupLikeRepository.findLikedPopups(userId);

        assertThat(liked).extracting(Popup::getTitle)
                .containsExactly("나중에찜한팝업", "먼저찜한팝업");
    }

    @Test
    void findLikedPopupIds는_찜한_것만_배치로_골라낸다() {
        Popup liked = newPopup("찜함");
        Popup notLiked = newPopup("안찜함");
        Long userId = 20L;
        popupLikeRepository.save(new PopupLike(liked, userId));
        entityManager.flush();
        entityManager.clear();

        List<Long> result = popupLikeRepository.findLikedPopupIds(
                userId, List.of(liked.getId(), notLiked.getId()));

        assertThat(result).containsExactly(liked.getId());
    }
}
