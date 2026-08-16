package com.duckspace.domain.post.dto.response;

import com.duckspace.domain.post.entity.ExchangeStatus;

import java.time.LocalDateTime;

public record ExchangePostSummaryResponse(
        Long id,
        String title,
        ExchangeStatus status,
        String offeredItemName,
        String wantedItemName,
        Long authorId,
        String authorNickname,
        LocalDateTime createdAt,
        long likeCount,
        long commentCount
) {
}
