package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.ExchangeApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExchangeApplicationRepository extends JpaRepository<ExchangeApplication, Long> {

    /** 특정 게시글에 달린 신청 목록. 글쓴이 확인은 서비스 레이어에서 합니다. */
    List<ExchangeApplication> findByPostIdOrderByAppliedAtDesc(Long postId);

    /** 내가 신청한 것("보낸" 신청함). */
    List<ExchangeApplication> findByApplicantUserIdOrderByAppliedAtDesc(Long applicantUserId);

    /** 내 게시글에 들어온 신청("받은" 신청함). ExchangeApplication은 postId만 들고 있어 Post와 조인합니다. */
    @Query("""
            select ea from ExchangeApplication ea
            join Post p on p.id = ea.postId
            where p.userId = :userId
            order by ea.appliedAt desc
            """)
    List<ExchangeApplication> findReceivedByUserId(@Param("userId") Long userId);
}
