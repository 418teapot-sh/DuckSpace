package com.duckspace.domain.exhibition.repository;

import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExhibitionItemRepository extends JpaRepository<ExhibitionItem, Long> {

    /**
     * 장식장 상세용.
     *
     * <p>노출할 상태를 인자로 받습니다. 주인에게는 처리 중·실패한 굿즈까지 보여야 조치할 수 있고,
     * 남에게는 완료된 것만 보여야 합니다. ({@link ItemStatus#visibleTo})
     */
    List<ExhibitionItem> findByExhibitionIdAndStatusInOrderByIdAsc(
            Long exhibitionId, Collection<ItemStatus> statuses);

    /** 그리드 최초 진입. 최신순. */
    List<ExhibitionItem> findByExhibitionIdAndStatusInOrderByIdDesc(
            Long exhibitionId, Collection<ItemStatus> statuses, Pageable pageable);

    /** 그리드 더보기. 커서보다 오래된 것만. */
    List<ExhibitionItem> findByExhibitionIdAndStatusInAndIdLessThanOrderByIdDesc(
            Long exhibitionId, Collection<ItemStatus> statuses, Long cursor, Pageable pageable);

    /**
     * 재처리 접수용. <b>행을 잠그고</b> 읽습니다.
     *
     * <p>재시도 버튼을 빠르게 두 번 누르면 두 요청이 모두 {@code FAILED} 를 읽고 통과해서,
     * 같은 사진이 두 번 처리됩니다. remove.bg 무료 크레딧(월 50회)이 두 번 나가고,
     * 늦게 끝난 쪽이 먼저 끝난 쪽의 결과를 덮을 수도 있습니다.
     *
     * <p>잠그면 뒤에 온 요청이 앞 트랜잭션의 커밋을 기다렸다가 {@code PENDING} 을 보고
     * 스스로 물러납니다.
     *
     * <p>장식장 조건을 쿼리에 넣은 이유: id 만으로 잠그면 남의 굿즈 id 를 추측해 호출하는
     * 것만으로 남의 행에 락 경합을 걸 수 있습니다. 조건에 안 걸리면 잠기지 않습니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ExhibitionItem i where i.id = :itemId and i.exhibition.id = :exhibitionId")
    Optional<ExhibitionItem> findOwnedForUpdate(@Param("itemId") Long itemId,
                                                @Param("exhibitionId") Long exhibitionId);

    /**
     * 백그라운드 결과 기록용 잠금 조회. 소유 확인이 필요 없는 내부 경로 전용입니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ExhibitionItem i where i.id = :itemId")
    Optional<ExhibitionItem> findByIdForUpdate(@Param("itemId") Long itemId);

    /**
     * {@code updatedAt} 만 지금 시각으로 강제 갱신합니다.
     *
     * <p>이미 PENDING 인 행에 {@code markPending()} 을 불러도 바뀐 값이 없어 Hibernate 가
     * UPDATE 를 생략하고, 방치 판정 기준인 {@code updatedAt} 이 그대로 남습니다. 그러면
     * 방금 재시도를 받아줬는데도 계속 "방치됨" 으로 보여 연타로 중복 재처리가 접수됩니다.
     * 재시도를 접수할 때 이걸 불러 방치 시계를 확실히 되감습니다.
     */
    @Modifying
    @Query("update ExhibitionItem i set i.updatedAt = :now where i.id = :itemId")
    void touchUpdatedAt(@Param("itemId") Long itemId, @Param("now") LocalDateTime now);

    void deleteByExhibitionId(Long exhibitionId);

    /** 공유 이미지 삭제 보호용 — 이 URL 을 배치해 둔 굿즈가 있는지. ({@code GoodsImageService} 의 409 판단) */
    boolean existsByImageUrl(String imageUrl);

    /**
     * 삭제 후보 중 <b>아직 굿즈가 배치 중인 URL</b> 만 골라냅니다. ({@code ImageCleanup} 전용)
     * URL 마다 exists 를 날리면 장식장 통째 삭제에서 쿼리가 수십 번 나가서 in 절로 묶습니다.
     */
    @Query("select distinct i.imageUrl from ExhibitionItem i where i.imageUrl in :urls")
    List<String> findReferencedUrls(@Param("urls") Collection<String> urls);

    /**
     * 장식장을 통째로 지우기 <b>전에</b> 정리할 이미지 주소만 모읍니다.
     * 엔티티를 다 불러올 필요가 없어서 주소 컬럼만 뽑습니다.
     */
    @Query("select i.imageUrl from ExhibitionItem i where i.exhibition.id = :exhibitionId and i.imageUrl is not null")
    List<String> findImageUrlsByExhibitionId(@Param("exhibitionId") Long exhibitionId);

    /**
     * 목록 카드 미리보기용 — <b>장식장마다 앞에서 {@code perExhibition} 개</b>의 굿즈 id 만 고릅니다.
     *
     * <p>예전에는 굿즈를 <b>전부</b> 가져온 뒤 자바에서 잘랐습니다. 응답 크기는 잡혔지만
     * DB 와 영속성 컨텍스트에는 그대로 다 올라왔습니다 — 굿즈 개수에 상한이 없어서, 한 장식장에
     * 2만 개가 쌓이면 <b>토큰 없는 요청 하나가 2만 엔티티</b>를 로드했습니다. 이 경로 상당수가
     * 인증 없이 열려 있어 반복 호출도 자유롭습니다.
     *
     * <p>그래서 상한을 SQL 로 내렸습니다. 자르는 지점이 한 곳뿐이라 자바 쪽에는 {@code limit} 이
     * 없습니다 — 여기를 고치면 그대로 상한이 바뀝니다.
     *
     * <p>JPQL 은 그룹별 상한을 표현하지 못해 네이티브 쿼리를 씁니다({@code row_number()} 윈도우
     * 함수, MySQL 8 이상). 엔티티가 아니라 <b>id 만</b> 돌려주고, 실제 조회는
     * {@link #findAllByIdsOrdered} 가 합니다 — 파생 테이블의 {@code rn} 컬럼이 엔티티 매핑에
     * 섞이지 않도록 하기 위해서입니다.
     *
     * @param status {@link ItemStatus} 의 이름. 컬럼이 varchar 라 문자열로 넘깁니다
     */
    @Query(value = """
            select t.id from (
                select i.id as id,
                       row_number() over (partition by i.exhibition_id order by i.id asc) as rn
                from exhibition_item i
                where i.exhibition_id in (:exhibitionIds) and i.status = :status
            ) t
            where t.rn <= :perExhibition
            """, nativeQuery = true)
    List<Long> findPreviewItemIds(@Param("exhibitionIds") Collection<Long> exhibitionIds,
                                  @Param("status") String status,
                                  @Param("perExhibition") int perExhibition);

    /**
     * id 목록으로 굿즈를 가져옵니다. {@code exhibition.id asc, id asc} 순이라
     * 상세 화면과 같은 순서로 그릴 수 있습니다.
     *
     * <p>{@link #findPreviewItemIds} 가 고른 id 를 받는 용도입니다. 장식장마다 따로 조회하면
     * N+1 이라 한 번에 가져옵니다.
     */
    @Query("""
            select i from ExhibitionItem i
            where i.id in :ids
            order by i.exhibition.id asc, i.id asc
            """)
    List<ExhibitionItem> findAllByIdsOrdered(@Param("ids") Collection<Long> ids);
}
