package com.duckspace.domain.exhibition.dto.request;

import com.duckspace.domain.exhibition.entity.Exhibition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateExhibitionRequest(
        @NotBlank @Size(max = Exhibition.NAME_MAX_LENGTH) String name
) {
}
