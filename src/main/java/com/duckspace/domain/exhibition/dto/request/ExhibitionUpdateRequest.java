package com.duckspace.domain.exhibition.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ExhibitionUpdateRequest(
        @NotBlank String title,
        String description,
        @NotBlank String thumbnailUrl,
        @NotEmpty List<@Valid ExhibitionItemRequest> items
) {
}