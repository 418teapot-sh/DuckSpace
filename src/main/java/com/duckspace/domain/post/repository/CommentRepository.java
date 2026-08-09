package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPost_IdOrderByIdAsc(Long postId);

    long countByPost_Id(Long postId);

    /** 목록 화면에서 게시글별 댓글 수를 한 번에 모아 가져오기 위한 배치 카운트. N+1 방지용. */
    @Query("select c.post.id as postId, count(c) as count from Comment c where c.post.id in :postIds group by c.post.id")
    List<PostIdCount> countByPostIdIn(@Param("postIds") List<Long> postIds);
}