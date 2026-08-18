package com.duckspace.domain.post.entity;

import com.duckspace.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@code POST /api/posts/images}로 업로드했지만 아직 어느 글에도 안 쓰인 이미지를 추적합니다.
 *
 * <p>업로드는 글 작성과 별도 요청이라, 사용자가 사진만 올려두고 글쓰기를 중간에 그만두면
 * 저장소(S3/로컬)엔 파일이 남는데 어디서도 참조하지 않는 고아가 됩니다. 글 작성 요청이
 * 실제로 그 URL을 쓰면(잡담 imageUrls / 교환 offeredItem·wantedItem.imageUrl) 이 행을 지워서
 * "아직 안 쓰였다"는 표시를 없애고, 오래 안 쓰인 채 남은 행은
 * {@link com.duckspace.domain.post.service.PendingPostImageCleaner}가 저장소 파일과 함께 정리합니다.
 */
@Entity
@Table(name = "pending_post_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingPostImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "image_url", nullable = false, unique = true, length = PostImage.IMAGE_URL_MAX_LENGTH)
    private String imageUrl;

    public PendingPostImage(Long userId, String imageUrl) {
        this.userId = userId;
        this.imageUrl = imageUrl;
    }
}
