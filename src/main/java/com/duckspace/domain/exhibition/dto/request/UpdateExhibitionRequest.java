package com.duckspace.domain.exhibition.dto.request;

import com.duckspace.domain.exhibition.entity.Exhibition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param themeCode 비우면 기존 테마를 그대로 둡니다.
 */
public record UpdateExhibitionRequest(
        @NotBlank @Size(max = Exhibition.NAME_MAX_LENGTH) String name,
        @Size(max = Exhibition.THEME_CODE_MAX_LENGTH) String themeCode
) {
}
