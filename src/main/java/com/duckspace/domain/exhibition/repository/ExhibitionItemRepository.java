package com.duckspace.domain.exhibition.repository;

import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

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

    void deleteByExhibitionId(Long exhibitionId);

    /**
     * 장식장을 통째로 지우기 <b>전에</b> 정리할 이미지 주소만 모읍니다.
     * 엔티티를 다 불러올 필요가 없어서 주소 컬럼만 뽑습니다.
     */
    @Query("select i.imageUrl from ExhibitionItem i where i.exhibition.id = :exhibitionId and i.imageUrl is not null")
    List<String> findImageUrlsByExhibitionId(@Param("exhibitionId") Long exhibitionId);

    /**
     * 장식장별 대표 이미지. 목록 카드에 쓸 첫 굿즈를 장식장마다 하나씩 한 번에 가져옵니다.
     * 장식장마다 따로 조회하면 N+1 이 됩니다.
     */
    @Query("""
            select i from ExhibitionItem i
            where i.id in (
                select min(i2.id) from ExhibitionItem i2
                where i2.exhibition.id in :exhibitionIds and i2.status = :status
                group by i2.exhibition.id
            )
            """)
    List<ExhibitionItem> findFirstItemOfEach(@Param("exhibitionIds") Collection<Long> exhibitionIds,
                                              @Param("status") ItemStatus status);
}
