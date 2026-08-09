package com.duckspace.domain.post.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 교환 글 3단계 — 내가 원하는 굿즈. */
public record WantedItemRequest(
        String imageUrl,
        @NotBlank String itemName,
        String brand
) {
}
