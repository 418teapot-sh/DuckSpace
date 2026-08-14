package com.duckspace.domain.banner.dto.response;

import com.duckspace.domain.banner.entity.Banner;

import java.time.LocalDateTime;

public record BannerResponse(
        Long id,
        String imageUrl,
        String title,
        String description,
        String aiSummary,
        Long popupId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int sortOrder,
        boolean active
) {
    public static BannerResponse from(Banner banner) {
        return new BannerResponse(
                banner.getId(),
                banner.getImageUrl(),
                banner.getTitle(),
                banner.getDescription(),
                banner.getAiSummary(),
                banner.getPopupId(),
                banner.getStartAt(),
                banner.getEndAt(),
                banner.getSortOrder(),
                banner.isActive()
        );
    }
}