package com.duckspace.domain.exhibition.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ExhibitionItemRequest(
        @NotBlank String name,
        @NotBlank String imageUrl,
        String description
) {
}