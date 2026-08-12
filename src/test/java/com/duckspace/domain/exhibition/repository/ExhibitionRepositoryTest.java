package com.duckspace.domain.exhibition.repository;

import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ExhibitionLike;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import com.duckspace.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인기 정렬·검색·대표이미지 쿼리는 group by / distinct / escape 가 얽혀 있어
 * 목(mock) 으로는 검증되지 않습니다. 실제 DB 로 확인합니다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class ExhibitionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ExhibitionRepository exhibitionRepository;

    @Autowired
    private ExhibitionItemRepository exhibitionItemRepository;

    @Autowired
    private ExhibitionLikeRepository exhibitionLikeRepository;

    private Exhibition exhibition(Long userId, String name) {
        return entityManager.persist(new Exhibition(userId, name, null));
    }

    private ExhibitionItem item(Exhibition e, String label, String itemName, ItemStatus status) {
        var placement = new ExhibitionItem.Placement(0.1, 0.2, 0.3, 0.3);
        return entityManager.persist(
                new ExhibitionItem(e, placement, "https://img/" + label + ".png", itemName, null, null, status));
    }

    private void like(Exhibition e, Long userId) {
        entityManager.persist(new ExhibitionLike(e, userId));
    }

    @Test
    @DisplayName("인기순 — 좋아요가 많은 순서로, 0개인 장식장도 포함된다")
    void 인기순_정렬() {
        Exhibition popular = exhibition(1L, "인기");
        Exhibition medium = exhibition(2L, "보통");
        Exhibition empty = exhibition(3L, "좋아요없음");

        like(popular, 10L);
        like(popular, 11L);
        like(popular, 12L);
        like(medium, 10L);
        entityManager.flush();

        List<Long> ids = exhibitionRepository.findPopularIds(PageRequest.of(0, 10));

        assertThat(ids).containsExactly(popular.getId(), medium.getId(), empty.getId());
    }

    @Test
    @DisplayName("인기순 — limit 만큼만 가져온다")
    void 인기순_limit() {
        Exhibition a = exhibition(1L, "A");
        exhibition(2L, "B");
        like(a, 10L);
        entityManager.flush();

        assertThat(exhibitionRepository.findPopularIds(PageRequest.of(0, 1))).containsExactly(a.getId());
    }

    @Test
    @DisplayName("검색 — 굿즈 이름으로 장식장을 찾는다")
    void 검색_이름매칭() {
        Exhibition matched = exhibition(1L, "이름매칭");
        Exhibition unrelated = exhibition(2L, "무관");

        item(matched, "a", "치이카와 인형", ItemStatus.READY);
        item(unrelated, "b", "포카", ItemStatus.READY);
        entityManager.flush();

        List<Long> ids = exhibitionRepository.searchExhibitionIdsByItem(
                "치이카와", ItemStatus.READY, PageRequest.of(0, 10));

        assertThat(ids).containsExactly(matched.getId());
    }

    @Test
    @DisplayName("검색 — 한 장식장에 여러 개가 걸려도 한 번만 나온다")
    void 검색_중복제거() {
        Exhibition e = exhibition(1L, "여러개");
        item(e, "a", "치이카와 인형", ItemStatus.READY);
        item(e, "b", "치이카와 키링", ItemStatus.READY);
        entityManager.flush();

        assertThat(exhibitionRepository.searchExhibitionIdsByItem("치이카와", ItemStatus.READY, PageRequest.of(0, 10)))
                .containsExactly(e.getId());
    }

    @Test
    @DisplayName("검색 — 처리 중(PENDING)인 굿즈는 결과에 나오지 않는다")
    void 검색_상태필터() {
        Exhibition e = exhibition(1L, "처리중");
        item(e, "a", "치이카와 인형", ItemStatus.PENDING);
        entityManager.flush();

        assertThat(exhibitionRepository.searchExhibitionIdsByItem("치이카와", ItemStatus.READY, PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    @DisplayName("검색 — % 를 넣어도 전체 검색이 되지 않는다 (와일드카드 이스케이프)")
    void 검색_와일드카드_이스케이프() {
        Exhibition withPercent = exhibition(1L, "퍼센트");
        Exhibition plain = exhibition(2L, "일반");
        item(withPercent, "a", "50%할인 키링", ItemStatus.READY);
        item(plain, "b", "그냥 키링", ItemStatus.READY);
        entityManager.flush();

        // 서비스가 넘기는 형태(이스케이프된 키워드)를 그대로 넣습니다.
        List<Long> ids = exhibitionRepository.searchExhibitionIdsByItem(
                "\\%", ItemStatus.READY, PageRequest.of(0, 10));

        assertThat(ids)
                .as("% 가 와일드카드로 동작하면 두 개 다 나옵니다")
                .containsExactly(withPercent.getId());
    }

    @Test
    @DisplayName("대표 이미지 — 장식장마다 가장 먼저 배치된 굿즈 하나씩만 가져온다")
    void 대표이미지_한번에조회() {
        Exhibition a = exhibition(1L, "A");
        Exhibition b = exhibition(2L, "B");
        ExhibitionItem firstOfA = item(a, "a1", "첫번째", ItemStatus.READY);
        item(a, "a2", "두번째", ItemStatus.READY);
        ExhibitionItem firstOfB = item(b, "b1", "B첫번째", ItemStatus.READY);
        entityManager.flush();

        List<ExhibitionItem> items = exhibitionItemRepository.findFirstItemOfEach(
                List.of(a.getId(), b.getId()), ItemStatus.READY);

        assertThat(items).extracting(ExhibitionItem::getId)
                .containsExactlyInAnyOrder(firstOfA.getId(), firstOfB.getId());
    }

    @Test
    @DisplayName("좋아요 수 — 여러 장식장을 한 번에 센다")
    void 좋아요수_한번에조회() {
        Exhibition a = exhibition(1L, "A");
        Exhibition b = exhibition(2L, "B");
        like(a, 10L);
        like(a, 11L);
        like(b, 10L);
        entityManager.flush();

        var counts = exhibitionLikeRepository.countByExhibitionIds(List.of(a.getId(), b.getId()));

        assertThat(counts).hasSize(2);
        assertThat(counts.stream()
                .filter(c -> c.getExhibitionId().equals(a.getId()))
                .findFirst().orElseThrow().getLikeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("내가 좋아요한 장식장만 골라낸다")
    void 내_좋아요_조회() {
        Exhibition a = exhibition(1L, "A");
        Exhibition b = exhibition(2L, "B");
        like(a, 10L);
        like(b, 99L);
        entityManager.flush();

        assertThat(exhibitionLikeRepository.findLikedExhibitionIds(10L, List.of(a.getId(), b.getId())))
                .containsExactly(a.getId());
    }

    @Test
    @DisplayName("자유 배치라 같은 자리에 굿즈를 여러 개 놓을 수 있다")
    void 위치가_겹쳐도_저장된다() {
        Exhibition e = exhibition(1L, "장식장");
        var samePlace = new ExhibitionItem.Placement(0.5, 0.5, 0.2, 0.2);

        entityManager.persist(new ExhibitionItem(e, samePlace, "u1", "먼저", null, null, ItemStatus.READY));
        entityManager.persist(new ExhibitionItem(e, samePlace, "u2", "나중", null, null, ItemStatus.READY));

        entityManager.flush();   // 제약 위반 없이 통과해야 합니다
        assertThat(exhibitionItemRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("상세 조회는 노출할 상태만 골라온다")
    void 상태별_노출_필터() {
        Exhibition e = exhibition(1L, "장식장");
        item(e, "ready", "완료된 굿즈", ItemStatus.READY);
        item(e, "pending", "처리중 굿즈", ItemStatus.PENDING);
        entityManager.flush();

        var ownerView = exhibitionItemRepository.findByExhibitionIdAndStatusInOrderByIdAsc(
                e.getId(), ItemStatus.visibleTo(true));
        var guestView = exhibitionItemRepository.findByExhibitionIdAndStatusInOrderByIdAsc(
                e.getId(), ItemStatus.visibleTo(false));

        assertThat(ownerView).as("주인은 처리 중인 것도 봐야 조치할 수 있습니다").hasSize(2);
        assertThat(guestView).as("남에게는 완료된 것만 보입니다").hasSize(1);
    }
}
