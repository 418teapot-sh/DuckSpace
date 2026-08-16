package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.ExchangeDetail;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExchangeDetailRepository extends JpaRepository<ExchangeDetail, Long> {

    /** Post를 같이 즉시로딩합니다. 소유자 확인 + 상세 조회를 한 번에 해야 할 때(신청 완료 처리 등) 쿼리를 하나 줄여줍니다. */
    @Query("select ed from ExchangeDetail ed join fetch ed.post where ed.postId = :postId")
    Optional<ExchangeDetail> findWithPostByPostId(@Param("postId") Long postId);

    /**
     * 같은 게시글에 대한 apply()/accept()가 동시에 들어와도 "게시글당 하나만 진행" 불변식이 깨지지
     * 않도록 잠급니다. 뒤에 들어온 트랜잭션은 앞선 트랜잭션의 커밋을 기다렸다가 최신 상태로 검사하게 됩니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ed from ExchangeDetail ed where ed.postId = :postId")
    Optional<ExchangeDetail> findByPostIdForUpdate(@Param("postId") Long postId);
}