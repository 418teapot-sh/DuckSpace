package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    List<PostImage> findByPost_IdOrderBySortOrderAsc(Long postId);

    /**
     * 목록 화면에서 게시글별 <b>대표 이미지 1장</b>을 한 번에 모아 가져옵니다. N+1 방지용입니다.
     *
     * <p>노출 순서가 {@code sortOrder} 오름차순이라 <b>0 번이 대표</b>입니다. 카드가 이미지를
     * 한 장만 그리므로 나머지는 가져오지 않습니다 — 잡담 글은 최대 4장까지 붙을 수 있어서,
     * 전부 실으면 쓰지도 않는 URL 이 목록 응답을 네 배로 불립니다.
     *
     * <p>이게 없을 때 프론트는 <b>카드마다 상세 API 를 따로 불러</b> 이미지를 채우고 있었습니다.
     * 그것도 마이페이지에서만이라, 잡담 목록에는 아예 사진이 안 나왔습니다.
     */
    @Query("""
            select i.post.id as postId, i.imageUrl as imageUrl
            from PostImage i
            where i.post.id in :postIds and i.sortOrder = :sortOrder
            """)
    List<PostThumbnail> findThumbnails(@Param("postIds") List<Long> postIds,
                                       @Param("sortOrder") int sortOrder);

    void deleteByPost_Id(Long postId);
}
