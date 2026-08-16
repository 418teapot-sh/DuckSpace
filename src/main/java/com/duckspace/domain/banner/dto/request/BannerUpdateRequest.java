package com.duckspace.domain.banner.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record BannerUpdateRequest(
        @NotBlank String imageUrl,
        @NotBlank String title,
        String description,
        @NotNull Long popupId,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        int sortOrder,
        /** 생략하면 true(노출)로 저장됩니다. 급하게 내려야 하면 false로 보내세요. */
        Boolean active
) {
}