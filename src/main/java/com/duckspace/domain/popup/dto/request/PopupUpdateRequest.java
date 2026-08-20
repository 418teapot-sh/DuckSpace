package com.duckspace.domain.popup.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PopupUpdateRequest(
        @NotBlank String title,
        @NotBlank String imageUrl,
        String description,
        String location,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String benefitImageUrl,
        String benefitDescription,
        String operatingHours
) {



}