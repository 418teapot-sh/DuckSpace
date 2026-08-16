package com.duckspace.domain.post.dto.response;

import com.duckspace.domain.post.entity.ExchangeApplicationStatus;
import com.duckspace.domain.post.entity.ItemCondition;

import java.time.LocalDateTime;

public record ExchangeApplicationResponse(
        Long id,
        Long postId,
        String postTitle,
        Long applicantUserId,
        String applicantNickname,
        String offeredItemName,
        String offeredImageUrl,
        String offeredBrand,
        ItemCondition offeredCondition,
        String message,
        ExchangeApplicationStatus status,
        LocalDateTime appliedAt,
        LocalDateTime respondedAt,
        LocalDateTime completedAt
) {
}
