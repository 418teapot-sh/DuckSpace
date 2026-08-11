package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 최상위 댓글만 오래된순(id asc) 커서 페이지네이션으로 조회합니다.
     * cursor가 null이면 처음부터, 값이 있으면 그보다 id가 큰(더 나중에 달린) 댓글부터 내려줍니다.
     * 답글은 별도로 {@link #findByParent_IdIn}으로 조회해 중첩시킵니다.
     */
    @Query("""
            select c from Comment c
            where c.post.id = :postId
              and c.parent is null
              and (:cursor is null or c.id > :cursor)
            order by c.id asc
            """)
    List<Comment> findTopLevelByPostId(@Param("postId") Long postId, @Param("cursor") Long cursor, Pageable pageable);

    List<Comment> findByParent_IdInOrderByIdAsc(List<Long> parentIds);

    long countByPost_Id(Long postId);

    /** 목록 화면에서 게시글별 댓글 수를 한 번에 모아 가져오기 위한 배치 카운트. N+1 방지용. */
    @Query("select c.post.id as postId, count(c) as count from Comment c where c.post.id in :postIds group by c.post.id")
    List<PostIdCount> countByPostIdIn(@Param("postIds") List<Long> postIds);
}