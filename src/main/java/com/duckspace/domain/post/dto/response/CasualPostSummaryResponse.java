package com.duckspace.domain.post.dto.response;

import java.time.LocalDateTime;

public record CasualPostSummaryResponse(
        Long id,
        String content,
        Long authorId,
        String authorNickname,
        LocalDateTime createdAt,
        long likeCount,
        long commentCount
) {
}
