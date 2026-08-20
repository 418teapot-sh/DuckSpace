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

    /**
     * 굿즈를 하나 넣어둔 장식장.
     *
     * <p>피드·인기순은 <b>굿즈가 없는 장식장을 거릅니다.</b> 그 두 쿼리의 정렬·커서를 보는
     * 테스트는 장식장이 필터에 걸려 사라지면 안 되므로 여기로 만듭니다.
     * 필터 자체를 보는 테스트는 {@code exhibition(...)} 으로 빈 것을 만들어 씁니다.
     */
    private Exhibition exhibitionWithItem(Long userId, String name) {
        Exhibition e = exhibition(userId, name);
        item(e, name, "굿즈", ItemStatus.READY);
        return e;
    }

    @Test
    @DisplayName("인기순 — 좋아요가 많은 순서로, 0개인 장식장도 포함된다")
    void 인기순_정렬() {
        Exhibition popular = exhibitionWithItem(1L, "인기");
        Exhibition medium = exhibitionWithItem(2L, "보통");
        Exhibition empty = exhibitionWithItem(3L, "좋아요없음");

        like(popular, 10L);
        like(popular, 11L);
        like(popular, 12L);
        like(medium, 10L);
        entityManager.flush();

        List<Long> ids = exhibitionRepository.findPopularIds(ItemStatus.READY, PageRequest.of(0, 10));

        assertThat(ids).containsExactly(popular.getId(), medium.getId(), empty.getId());
    }

    @Test
    @DisplayName("인기순 — limit 만큼만 가져온다")
    void 인기순_limit() {
        Exhibition a = exhibitionWithItem(1L, "A");
        exhibitionWithItem(2L, "B");
        like(a, 10L);
        entityManager.flush();

        assertThat(exhibitionRepository.findPopularIds(ItemStatus.READY, PageRequest.of(0, 1))).containsExactly(a.getId());
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
    @DisplayName("카드 미리보기 — 여러 장식장의 굿즈를 한 번에, id asc 순으로, READY 만 가져온다")
    void 굿즈_한번에조회() {
        Exhibition a = exhibition(1L, "A");
        Exhibition b = exhibition(2L, "B");
        ExhibitionItem a1 = item(a, "a1", "먼저", ItemStatus.READY);
        ExhibitionItem a2 = item(a, "a2", "나중", ItemStatus.READY);
        item(a, "a3", "처리중", ItemStatus.PENDING);
        ExhibitionItem b1 = item(b, "b1", "B굿즈", ItemStatus.READY);
        entityManager.flush();

        List<Long> ids = exhibitionItemRepository.findPreviewItemIds(
                List.of(a.getId(), b.getId()), ItemStatus.READY.name(), 20);
        List<ExhibitionItem> items = exhibitionItemRepository.findAllByIdsOrdered(ids);

        assertThat(items).extracting(ExhibitionItem::getId)
                .as("장식장 A의 READY 굿즈 2개(id asc) 다음 장식장 B의 굿즈, PENDING은 제외")
                .containsExactly(a1.getId(), a2.getId(), b1.getId());
    }

    @Test
    @DisplayName("피드 — 굿즈가 없는 장식장은 빠지고, 내 장식장 목록에는 그대로 남는다")
    void 피드는_빈_장식장을_거른다() {
        // 가입할 때마다 기본 장식장이 자동으로 생깁니다. 거르지 않으면 첫 페이지가 빈 카드로
        // 도배되고, 굿즈를 실제로 올린 장식장이 뒤로 밀립니다.
        Exhibition empty = exhibition(1L, "가입하고 안 쓴 장식장");
        Exhibition filled = exhibition(1L, "굿즈 올린 장식장");
        item(filled, "f1", "굿즈", ItemStatus.READY);

        // 사진이 아직 처리 중이면 카드에 그릴 그림이 없으므로 빈 것으로 봅니다.
        Exhibition pendingOnly = exhibition(2L, "업로드 직후");
        item(pendingOnly, "p1", "처리중", ItemStatus.PENDING);
        entityManager.flush();

        List<Long> feed = exhibitionRepository.findRecentIds(
                null, ItemStatus.READY, PageRequest.of(0, 10));

        assertThat(feed)
                .as("READY 굿즈가 있는 장식장만 나와야 합니다")
                .containsExactly(filled.getId());

        List<Long> popular = exhibitionRepository.findPopularIds(
                ItemStatus.READY, PageRequest.of(0, 10));
        assertThat(popular).as("인기순도 같은 기준입니다").containsExactly(filled.getId());

        // 반대로 자기 장식장 목록에서 빈 것이 사라지면 방금 만든 장식장을 찾을 수 없습니다.
        List<Long> mine = exhibitionRepository.findIdsByUserId(1L, PageRequest.of(0, 10));
        assertThat(mine)
                .as("내 장식장 목록에는 빈 장식장도 그대로 나와야 합니다")
                .containsExactly(empty.getId(), filled.getId());
    }

    @Test
    @DisplayName("피드 — 빈 장식장을 거른 뒤에도 커서 페이징이 어긋나지 않는다")
    void 빈_장식장을_거른_커서_페이징() {
        // 필터가 쿼리에 있어야 하는 이유입니다. 서비스에서 걸러내면 pageSize+1 개를 받아
        // 그중 다수가 빠져 페이지가 짧아지고, hasNext 는 필터 전 개수로 계산돼 틀립니다.
        Exhibition first = exhibition(1L, "굿즈1");
        item(first, "i1", "굿즈", ItemStatus.READY);
        exhibition(1L, "빈 장식장1");
        Exhibition second = exhibition(1L, "굿즈2");
        item(second, "i2", "굿즈", ItemStatus.READY);
        exhibition(1L, "빈 장식장2");
        Exhibition third = exhibition(1L, "굿즈3");
        item(third, "i3", "굿즈", ItemStatus.READY);
        entityManager.flush();

        // 최신순(id desc)이라 third → second → first. 2개짜리 페이지 + 1 개를 요청합니다.
        List<Long> page1 = exhibitionRepository.findRecentIds(
                null, ItemStatus.READY, PageRequest.of(0, 3));
        assertThat(page1)
                .as("빈 장식장이 섞여 있어도 굿즈 있는 것만 세 개가 채워집니다")
                .containsExactly(third.getId(), second.getId(), first.getId());

        List<Long> page2 = exhibitionRepository.findRecentIds(
                second.getId(), ItemStatus.READY, PageRequest.of(0, 3));
        assertThat(page2)
                .as("커서보다 오래된 것 중에서도 빈 장식장은 빠집니다")
                .containsExactly(first.getId());
    }

    @Test
    @DisplayName("카드 미리보기 — 상한이 장식장마다 따로 걸린다 (전체 개수가 아니라)")
    void 미리보기_상한은_장식장별() {
        // 예전에는 굿즈를 전부 가져온 뒤 자바에서 잘랐습니다. 응답만 잡히고 DB·영속성 컨텍스트에는
        // 그대로 다 올라와서, 굿즈가 많은 장식장 하나가 인증 없는 요청 하나로 메모리를 먹었습니다.
        Exhibition many = exhibition(1L, "굿즈가 많은 장식장");
        Exhibition few = exhibition(2L, "적은 장식장");
        for (int i = 0; i < 25; i++) {
            item(many, "m" + i, "굿즈" + i, ItemStatus.READY);
        }
        item(few, "f1", "하나", ItemStatus.READY);
        item(few, "f2", "둘", ItemStatus.READY);
        entityManager.flush();

        List<Long> ids = exhibitionItemRepository.findPreviewItemIds(
                List.of(many.getId(), few.getId()), ItemStatus.READY.name(), 20);

        assertThat(ids)
                .as("많은 쪽 20개(25개 중) + 적은 쪽 2개 = 22개. 전체 상한이었다면 20개가 나옵니다")
                .hasSize(22);

        List<ExhibitionItem> items = exhibitionItemRepository.findAllByIdsOrdered(ids);
        assertThat(items).filteredOn(i -> i.getExhibition().getId().equals(many.getId()))
                .as("한 장식장에서 가져오는 개수가 상한을 넘지 않아야 합니다")
                .hasSize(20);
        assertThat(items).filteredOn(i -> i.getExhibition().getId().equals(few.getId()))
                .as("굿즈가 적은 장식장은 있는 만큼만 나옵니다")
                .hasSize(2);
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
        Exhibition oldest = exhibitionWithItem(1L, "먼저등록");
        Exhibition newest = exhibitionWithItem(2L, "나중등록");
        // 인기순과 다르다는 걸 보여주려고 오래된 쪽에 좋아요를 몰아줍니다.
        like(oldest, 10L);
        like(oldest, 11L);
        entityManager.flush();

        List<Long> ids = exhibitionRepository.findRecentIds(null, ItemStatus.READY, PageRequest.of(0, 10));

        assertThat(ids).containsExactly(newest.getId(), oldest.getId());
    }

    @Test
    @DisplayName("검색 탭 피드 — size 만큼만 가져온다")
    void 최신순_limit() {
        exhibitionWithItem(1L, "A");
        Exhibition newest = exhibitionWithItem(2L, "B");
        entityManager.flush();

        assertThat(exhibitionRepository.findRecentIds(null, ItemStatus.READY, PageRequest.of(0, 1)))
                .containsExactly(newest.getId());
    }

    @Test
    @DisplayName("검색 탭 피드 더보기 — 커서보다 오래된 것만 가져온다")
    void 최신순_커서() {
        Exhibition oldest = exhibitionWithItem(1L, "A");
        Exhibition middle = exhibitionWithItem(2L, "B");
        exhibitionWithItem(3L, "C");
        entityManager.flush();

        List<Long> ids = exhibitionRepository.findRecentIds(middle.getId() + 1, ItemStatus.READY, PageRequest.of(0, 10));

        assertThat(ids).containsExactly(middle.getId(), oldest.getId());
    }

    @Test
    @DisplayName("검색 탭 피드 — cursor 가 null 이면 첫 페이지 전체를 가져온다")
    void 최신순_커서_null이면_첫페이지() {
        Exhibition oldest = exhibitionWithItem(1L, "A");
        Exhibition newest = exhibitionWithItem(2L, "B");
        entityManager.flush();

        List<Long> ids = exhibitionRepository.findRecentIds(null, ItemStatus.READY, PageRequest.of(0, 10));

        assertThat(ids).containsExactly(newest.getId(), oldest.getId());
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

    @Test
    @DisplayName("내 장식장 목록은 커서로 51번째 이후에도 닿는다")
    void 내_장식장_커서_더보기() {
        // 상한(50)만 있고 커서가 없으면, 자기 장식장인데도 그 뒤로는 볼 방법이 없었습니다.
        Exhibition first = exhibition(1L, "1번");
        Exhibition second = exhibition(1L, "2번");
        Exhibition third = exhibition(1L, "3번");
        exhibition(2L, "남의 것");
        entityManager.flush();

        List<Long> page1 = exhibitionRepository.findIdsByUserId(1L, PageRequest.of(0, 2));
        assertThat(page1).containsExactly(first.getId(), second.getId());

        List<Long> page2 = exhibitionRepository.findIdsByUserIdAfter(1L, page1.get(1), PageRequest.of(0, 2));
        assertThat(page2)
                .as("커서 뒤엣것만, 남의 장식장은 섞이지 않아야 합니다")
                .containsExactly(third.getId());
    }

    @Test
    @DisplayName("장식장의 좋아요를 벌크 쿼리 한 번으로 지운다")
    void 좋아요_일괄_삭제() {
        // 파생 deleteBy... 는 행을 전부 SELECT 해서 영속성 컨텍스트에 올린 뒤 한 건씩
        // 지웁니다. 좋아요가 많은 장식장을 지우면 그만큼 DELETE 가 나갑니다.
        Exhibition target = exhibition(1L, "지울 장식장");
        Exhibition other = exhibition(2L, "남을 장식장");
        entityManager.persist(new ExhibitionLike(target, 10L));
        entityManager.persist(new ExhibitionLike(target, 11L));
        entityManager.persist(new ExhibitionLike(other, 10L));
        entityManager.flush();

        exhibitionLikeRepository.deleteByExhibitionId(target.getId());
        entityManager.clear();   // 벌크 쿼리는 영속성 컨텍스트를 건너뛰므로 다시 읽습니다.

        assertThat(exhibitionLikeRepository.countByExhibitionId(target.getId())).isZero();
        assertThat(exhibitionLikeRepository.countByExhibitionId(other.getId()))
                .as("다른 장식장의 좋아요는 그대로여야 합니다")
                .isEqualTo(1);
    }
}
