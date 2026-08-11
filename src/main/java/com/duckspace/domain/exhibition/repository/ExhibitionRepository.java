package com.duckspace.domain.exhibition.repository;

import com.duckspace.domain.exhibition.entity.Exhibition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExhibitionRepository extends JpaRepository<Exhibition, Long> {

    /** 인기순 = 조회수 + 좋아요 * 5 (좋아요가 조회보다 강한 신호라 가중치를 더 둠). 배치 없이 조회 시점에 실시간 계산. */
    @Query("""
            SELECT e FROM Exhibition e
            ORDER BY e.viewCount + e.likeCount * 5 DESC, e.id DESC
            """)
    Page<Exhibition> findAllOrderByPopularity(Pageable pageable);

    @Query("""
            SELECT DISTINCT e FROM Exhibition e
            LEFT JOIN FETCH e.items
            WHERE e.id = :exhibitionId
            """)
    Optional<Exhibition> findByIdWithItems(@Param("exhibitionId") Long exhibitionId);
}