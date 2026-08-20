package com.duckspace.domain.banner.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record BannerCreateRequest(
        @NotBlank String imageUrl,
        @NotBlank String title,
        String description,
        /** 없으면 팝업과 무관한 순수 광고 배너로 등록된다(클릭해도 이동 없음). */
        Long popupId,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        int sortOrder,
        /** 생략하면 true(노출)로 등록됩니다. */
        Boolean active
) {
}