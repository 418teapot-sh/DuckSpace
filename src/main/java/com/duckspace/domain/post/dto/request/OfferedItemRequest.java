package com.duckspace.domain.post.dto.request;

import com.duckspace.domain.post.entity.ItemCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 교환 글 2단계 — 내가 가진 굿즈. */
public record OfferedItemRequest(
        String imageUrl,
        @NotBlank String itemName,
        String brand,
        @NotNull ItemCondition condition
) {
}
