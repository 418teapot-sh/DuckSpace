package com.duckspace.domain.post.dto.request;

import com.duckspace.domain.post.entity.Comment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        @NotBlank @Size(max = Comment.CONTENT_MAX_LENGTH) String content,
        Long parentId,
        boolean secret
) {
}
