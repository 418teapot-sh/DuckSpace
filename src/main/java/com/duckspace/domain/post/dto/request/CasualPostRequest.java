package com.duckspace.domain.post.dto.request;

import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.entity.PostHashtag;
import com.duckspace.domain.post.entity.PostImage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 잡담 글 작성/수정 요청. */
public record CasualPostRequest(
        @NotBlank @Size(max = Post.CASUAL_CONTENT_MAX_LENGTH) String content,
        @Size(max = PostImage.MAX_COUNT) List<@NotBlank String> imageUrls,
        List<@NotBlank @Size(max = PostHashtag.TAG_MAX_LENGTH) String> hashtags
) {
}
