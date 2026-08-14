package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.ExchangeApplication;
import com.duckspace.domain.post.entity.ExchangeApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExchangeApplicationRepository extends JpaRepository<ExchangeApplication, Long> {

    /** 특정 게시글에 달린 신청 목록. 글쓴이 확인은 서비스 레이어에서 합니다. */
    List<ExchangeApplication> findByPostIdOrderByAppliedAtDescIdDesc(Long postId);

    /** 내가 신청한 것("보낸" 신청함). */
    List<ExchangeApplication> findByApplicantUserIdOrderByAppliedAtDescIdDesc(Long applicantUserId);

    /** 내 게시글에 들어온 신청("받은" 신청함). ExchangeApplication은 postId만 들고 있어 Post와 조인합니다. */
    @Query("""
            select ea from ExchangeApplication ea
            join Post p on p.id = ea.postId
            where p.userId = :userId
            order by ea.appliedAt desc, ea.id desc
            """)
    List<ExchangeApplication> findReceivedByUserId(@Param("userId") Long userId);

    /** 같은 사람이 같은 글에 대기중인(status) 신청을 또 넣는 걸 막을 때 씁니다. */
    boolean existsByPostIdAndApplicantUserIdAndStatus(Long postId, Long applicantUserId, ExchangeApplicationStatus status);

    /** 한 게시글에 ACCEPTED/COMPLETED 신청이 이미 있는지 — accept() 시 중복 수락을 막을 때 씁니다. */
    boolean existsByPostIdAndStatusIn(Long postId, Collection<ExchangeApplicationStatus> statuses);

    /** accept()로 한 게시글당 최대 하나만 ACCEPTED가 되도록 보장되므로(existsByPostIdAndStatusIn), 있어도 하나뿐입니다. */
    Optional<ExchangeApplication> findByPostIdAndStatus(Long postId, ExchangeApplicationStatus status);
}
