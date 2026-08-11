package com.duckspace.domain.banner.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record BannerCreateRequest(
        @NotBlank String imageUrl,
        @NotBlank String title,
        String description,
        @NotNull Long popupId,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        int sortOrder
) {
}