package com.duckspace.domain.post.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record CommentResponse(
        Long id,
        String content,
        Long authorId,
        String authorNickname,
        boolean mine,
        boolean secret,
        LocalDateTime createdAt,
        List<CommentResponse> replies
) {
}
