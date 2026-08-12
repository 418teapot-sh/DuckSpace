package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    long countByPostId(Long postId);

    void deleteByPostId(Long postId);

    /** 목록 화면에서 게시글별 좋아요 수를 한 번에 모아 가져오기 위한 배치 카운트. N+1 방지용. */
    @Query("select l.postId as postId, count(l) as count from PostLike l where l.postId in :postIds group by l.postId")
    List<PostIdCount> countByPostIdIn(@Param("postIds") List<Long> postIds);
}
