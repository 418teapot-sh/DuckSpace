package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.ExchangeApplication;
import com.duckspace.domain.post.entity.ExchangeApplicationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExchangeApplicationRepository extends JpaRepository<ExchangeApplication, Long> {

    /**
     * 특정 게시글에 달린 신청 목록. 최신순(id desc) 커서 페이징입니다. 글쓴이 확인은 서비스 레이어에서 합니다.
     * cursor가 null이면 최신 신청부터, 값이 있으면 그보다 id가 작은 신청부터 내려줍니다.
     */
    @Query("""
            select ea from ExchangeApplication ea
            where ea.postId = :postId
              and (:cursor is null or ea.id < :cursor)
            order by ea.id desc
            """)
    List<ExchangeApplication> findByPostId(@Param("postId") Long postId, @Param("cursor") Long cursor, Pageable pageable);

    /** 내가 신청한 것("보낸" 신청함). 최신순 커서 페이징은 findByPostId와 같은 규칙입니다. */
    @Query("""
            select ea from ExchangeApplication ea
            where ea.applicantUserId = :applicantUserId
              and (:cursor is null or ea.id < :cursor)
            order by ea.id desc
            """)
    List<ExchangeApplication> findByApplicantUserId(@Param("applicantUserId") Long applicantUserId,
                                                      @Param("cursor") Long cursor, Pageable pageable);

    /** 내 게시글에 들어온 신청("받은" 신청함). ExchangeApplication은 postId만 들고 있어 Post와 조인합니다. */
    @Query("""
            select ea from ExchangeApplication ea
            join Post p on p.id = ea.postId
            where p.userId = :userId
              and (:cursor is null or ea.id < :cursor)
            order by ea.id desc
            """)
    List<ExchangeApplication> findReceivedByUserId(@Param("userId") Long userId,
                                                     @Param("cursor") Long cursor, Pageable pageable);

    /** 같은 사람이 같은 글에 진행 중인(APPLIED/ACCEPTED) 신청을 이미 갖고 있으면 또 신청하는 걸 막을 때 씁니다. */
    boolean existsByPostIdAndApplicantUserIdAndStatusIn(Long postId, Long applicantUserId,
                                                          Collection<ExchangeApplicationStatus> statuses);

    /** 한 게시글에 ACCEPTED/COMPLETED 신청이 이미 있는지 — accept() 시 중복 수락을 막을 때 씁니다. */
    boolean existsByPostIdAndStatusIn(Long postId, Collection<ExchangeApplicationStatus> statuses);

    /** accept()로 한 게시글당 최대 하나만 ACCEPTED가 되도록 보장되므로(existsByPostIdAndStatusIn), 있어도 하나뿐입니다. */
    Optional<ExchangeApplication> findByPostIdAndStatus(Long postId, ExchangeApplicationStatus status);
}
