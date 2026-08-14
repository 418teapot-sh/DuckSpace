package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.ExchangeDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExchangeDetailRepository extends JpaRepository<ExchangeDetail, Long> {

    /** Post를 같이 즉시로딩합니다. 소유자 확인 + 상세 조회를 한 번에 해야 할 때(신청 완료 처리 등) 쿼리를 하나 줄여줍니다. */
    @Query("select ed from ExchangeDetail ed join fetch ed.post where ed.postId = :postId")
    Optional<ExchangeDetail> findWithPostByPostId(@Param("postId") Long postId);
}