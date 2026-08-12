package com.duckspace.domain.post.dto.request;

import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.entity.PostHashtag;
import com.duckspace.domain.post.entity.PostImage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 잡담 글 작성/수정 요청.
 *
 * <p>작성({@code POST})에서는 content가 필수입니다. 수정({@code PATCH})에서는 imageUrls/hashtags와
 * 마찬가지로 content도 null이면 기존 값을 그대로 둡니다 — 필수 여부는 {@code PostService}에서 검사합니다.
 */
public record CasualPostRequest(
        @Size(max = Post.CASUAL_CONTENT_MAX_LENGTH) String content,
        @Size(max = PostImage.MAX_COUNT) List<@NotBlank @Size(max = PostImage.IMAGE_URL_MAX_LENGTH) String> imageUrls,
        @Size(max = PostHashtag.MAX_COUNT) List<@NotBlank @Size(max = PostHashtag.TAG_MAX_LENGTH) String> hashtags
) {
}
