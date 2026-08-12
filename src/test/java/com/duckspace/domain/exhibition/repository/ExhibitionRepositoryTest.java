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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        return entityManager.persist(new Exhibition(userId, name));
    }

    private ExhibitionItem item(Exhibition e, String slotId, String itemName, String brand, ItemStatus status) {
        return entityManager.persist(
                new ExhibitionItem(e, slotId, "https://img/" + slotId + ".png", itemName, brand, null, status));
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
    @DisplayName("검색 — 굿즈 이름과 브랜드 양쪽에서 찾는다")
    void 검색_이름과_브랜드() {
        Exhibition byName = exhibition(1L, "이름매칭");
        Exhibition byBrand = exhibition(2L, "브랜드매칭");
        Exhibition unrelated = exhibition(3L, "무관");

        item(byName, "SHELF_1", "치이카와 인형", "산리오", ItemStatus.READY);
        item(byBrand, "SHELF_1", "키링", "치이카와", ItemStatus.READY);
        item(unrelated, "SHELF_1", "포카", "하이브", ItemStatus.READY);
        entityManager.flush();

        List<Long> ids = exhibitionRepository.searchExhibitionIdsByItem(
                "치이카와", ItemStatus.READY, PageRequest.of(0, 10));

        assertThat(ids).containsExactlyInAnyOrder(byName.getId(), byBrand.getId());
    }

    @Test
    @DisplayName("검색 — 한 장식장에 여러 개가 걸려도 한 번만 나온다")
    void 검색_중복제거() {
        Exhibition e = exhibition(1L, "여러개");
        item(e, "SHELF_1", "치이카와 인형", null, ItemStatus.READY);
        item(e, "SHELF_2", "치이카와 키링", null, ItemStatus.READY);
        entityManager.flush();

        assertThat(exhibitionRepository.searchExhibitionIdsByItem("치이카와", ItemStatus.READY, PageRequest.of(0, 10)))
                .containsExactly(e.getId());
    }

    @Test
    @DisplayName("검색 — 처리 중(PENDING)인 굿즈는 결과에 나오지 않는다")
    void 검색_상태필터() {
        Exhibition e = exhibition(1L, "처리중");
        item(e, "SHELF_1", "치이카와 인형", null, ItemStatus.PENDING);
        entityManager.flush();

        assertThat(exhibitionRepository.searchExhibitionIdsByItem("치이카와", ItemStatus.READY, PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    @DisplayName("검색 — % 를 넣어도 전체 검색이 되지 않는다 (와일드카드 이스케이프)")
    void 검색_와일드카드_이스케이프() {
        Exhibition withPercent = exhibition(1L, "퍼센트");
        Exhibition plain = exhibition(2L, "일반");
        item(withPercent, "SHELF_1", "50%할인 키링", null, ItemStatus.READY);
        item(plain, "SHELF_1", "그냥 키링", null, ItemStatus.READY);
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
        ExhibitionItem firstOfA = item(a, "SHELF_1", "첫번째", null, ItemStatus.READY);
        item(a, "SHELF_2", "두번째", null, ItemStatus.READY);
        ExhibitionItem firstOfB = item(b, "SHELF_1", "B첫번째", null, ItemStatus.READY);
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
    @DisplayName("같은 슬롯에 굿즈를 두 개 놓을 수 없다")
    void 슬롯_중복_불가() {
        Exhibition e = exhibition(1L, "장식장");
        item(e, "SHELF_1", "먼저", null, ItemStatus.READY);
        entityManager.flush();

        assertThrows(DataIntegrityViolationException.class, () -> {
            exhibitionItemRepository.saveAndFlush(
                    new ExhibitionItem(e, "SHELF_1", "url", "나중", null, null, ItemStatus.READY));
        });
    }

    @Test
    @DisplayName("다른 장식장이면 같은 슬롯 이름을 써도 된다")
    void 장식장이_다르면_같은_슬롯_가능() {
        Exhibition a = exhibition(1L, "A");
        Exhibition b = exhibition(1L, "B");
        item(a, "SHELF_1", "A의 굿즈", null, ItemStatus.READY);
        item(b, "SHELF_1", "B의 굿즈", null, ItemStatus.READY);

        entityManager.flush();   // 예외 없이 통과해야 합니다
        assertThat(exhibitionItemRepository.count()).isEqualTo(2);
    }
}
