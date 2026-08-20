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

    /**
     * 이 목록에서 <b>내가 좋아요를 누른 글</b>의 id 만 골라옵니다. N+1 방지용입니다.
     *
     * <p>이게 없을 때 목록 응답에 {@code liked} 를 못 실어서, 프론트가 <b>카드마다 상세 API 를
     * 따로 불러</b> 채우고 있었습니다. 그 요청이 늦거나 실패하면 <b>눌러둔 좋아요가 안 눌린 것처럼
     * 보입니다</b> — 성능만이 아니라 눈에 보이는 버그였습니다.
     *
     * <p>{@code existsByPostIdAndUserId} 를 글마다 부르는 것과 결과는 같고 쿼리는 한 번입니다.
     */
    @Query("select l.postId from PostLike l where l.userId = :userId and l.postId in :postIds")
    List<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
}
