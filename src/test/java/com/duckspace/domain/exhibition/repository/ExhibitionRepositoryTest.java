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
    @DisplayName("카드 미리보기용 굿즈 전체 — 여러 장식장의 굿즈를 한 번에, id asc 순으로 가져온다")
    void 굿즈_전체_한번에조회() {
        Exhibition a = exhibition(1L, "A");
        Exhibition b = exhibition(2L, "B");
        ExhibitionItem a1 = item(a, "a1", "먼저", ItemStatus.READY);
        ExhibitionItem a2 = item(a, "a2", "나중", ItemStatus.READY);
        item(a, "a3", "처리중", ItemStatus.PENDING);
        ExhibitionItem b1 = item(b, "b1", "B굿즈", ItemStatus.READY);
        entityManager.flush();

        List<ExhibitionItem> items = exhibitionItemRepository.findAllByExhibitionIdsAndStatus(
                List.of(a.getId(), b.getId()), ItemStatus.READY);

        assertThat(items).extracting(ExhibitionItem::getId)
                .as("장식장 A의 READY 굿즈 2개(id asc) 다음 장식장 B의 굿즈, PENDING은 제외")
                .containsExactly(a1.getId(), a2.getId(), b1.getId());
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
    @DisplayName("검색 탭 피드 — 좋아요와 무관하게 최신 등록순(id desc)이다")
    void 최신순_정렬() {
        Exhibition oldest = exhibition(1L, "먼저등록");
        Exhibition newest = exhibition(2L, "나중등록");
        // 인기순과 다르다는 걸 보여주려고 오래된 쪽에 좋아요를 몰아줍니다.
        like(oldest, 10L);
        like(oldest, 11L);
        entityManager.flush();

        List<Long> ids = exhibitionRepository.findRecentIds(PageRequest.of(0, 10));

        assertThat(ids).containsExactly(newest.getId(), oldest.getId());
    }

    @Test
    @DisplayName("검색 탭 피드 — limit 만큼만 가져온다")
    void 최신순_limit() {
        exhibition(1L, "A");
        Exhibition newest = exhibition(2L, "B");
        entityManager.flush();

        assertThat(exhibitionRepository.findRecentIds(PageRequest.of(0, 1))).containsExactly(newest.getId());
    }

    @Test
    @DisplayName("내 장식장만, 만든 순서대로 가져온다")
    void 내_장식장_목록() {
        Exhibition mineOld = exhibition(1L, "내 첫 장식장");
        Exhibition othersOne = exhibition(2L, "남의 장식장");
        Exhibition mineNew = exhibition(1L, "내 두번째 장식장");
        entityManager.flush();

        List<Long> ids = exhibitionRepository.findIdsByUserId(1L, PageRequest.of(0, 20));

        assertThat(ids)
                .as("먼저 만든 것이 먼저 나와야 합니다")
                .containsExactly(mineOld.getId(), mineNew.getId());
        assertThat(ids).doesNotContain(othersOne.getId());
    }

    @Test
    @DisplayName("장식장이 하나도 없으면 빈 목록이다")
    void 내_장식장이_없으면_빈_목록() {
        exhibition(2L, "남의 장식장");
        entityManager.flush();

        assertThat(exhibitionRepository.findIdsByUserId(1L, PageRequest.of(0, 20))).isEmpty();
    }

    /**
     * 비로그인 사용자를 위한 계약입니다.
     *
     * <p>홈 화면({@code /api/home})은 인증 없이 열리므로 {@code viewerId} 가 {@code null} 인 채로
     * 여기까지 내려옵니다. <b>터지지 않고 "좋아요 안 누름" 으로 취급되어야</b> 합니다.
     *
     * <p>두 메서드가 같은 결과를 내지만 <b>이유가 다릅니다.</b> JPQL 은 {@code = null} 이 항상
     * UNKNOWN 이라 비고, 파생 쿼리는 Spring Data 가 {@code IS NULL} 로 바꿔주는데 user_id 가
     * NOT NULL 이라 걸리는 행이 없습니다. 어느 쪽도 우연이 아니지만 근거가 달라서 같이 묶어둡니다.
     */
    @Test
    @DisplayName("비로그인(viewerId=null)이어도 좋아요 조회가 터지지 않고 빈 결과가 된다")
    void 비로그인_좋아요_조회() {
        Exhibition a = exhibition(1L, "A");
        like(a, 10L);
        entityManager.flush();

        assertThat(exhibitionLikeRepository.findLikedExhibitionIds(null, List.of(a.getId())))
                .as("비로그인 사용자에게는 likedByMe 가 전부 false 여야 합니다")
                .isEmpty();

        assertThat(exhibitionLikeRepository.existsByExhibitionIdAndUserId(a.getId(), null))
                .as("파생 쿼리도 null 로 터지지 않아야 합니다")
                .isFalse();
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
