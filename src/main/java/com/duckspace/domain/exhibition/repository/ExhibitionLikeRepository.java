package com.duckspace.domain.exhibition.repository;

import com.duckspace.domain.exhibition.entity.ExhibitionLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ExhibitionLikeRepository extends JpaRepository<ExhibitionLike, Long> {

    boolean existsByExhibitionIdAndUserId(Long exhibitionId, Long userId);

    /** 장식장 하나의 좋아요 수. 목록용 배치 쿼리를 한 건에 쓰지 않도록 별도로 둡니다. */
    long countByExhibitionId(Long exhibitionId);

    long deleteByExhibitionIdAndUserId(Long exhibitionId, Long userId);

    void deleteByExhibitionId(Long exhibitionId);

    /** 여러 장식장의 좋아요 수를 한 번에. 목록에서 장식장마다 세면 N+1 이 됩니다. */
    @Query("""
            select l.exhibition.id as exhibitionId, count(l.id) as likeCount
            from ExhibitionLike l
            where l.exhibition.id in :exhibitionIds
            group by l.exhibition.id
            """)
    List<LikeCount> countByExhibitionIds(@Param("exhibitionIds") Collection<Long> exhibitionIds);

    /** 내가 좋아요한 장식장 id 목록. 목록 응답의 likedByMe 를 채우는 데 씁니다. */
    @Query("select l.exhibition.id from ExhibitionLike l where l.userId = :userId and l.exhibition.id in :exhibitionIds")
    List<Long> findLikedExhibitionIds(@Param("userId") Long userId,
                                       @Param("exhibitionIds") Collection<Long> exhibitionIds);

    interface LikeCount {
        Long getExhibitionId();

        long getLikeCount();
    }
}
