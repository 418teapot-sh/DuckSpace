package com.duckspace.domain.exhibition.dto.request;

import com.duckspace.domain.exhibition.entity.Exhibition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param themeCode 배경 테마. 비우면 기본 테마({@code BASIC})가 적용됩니다.
 */
public record CreateExhibitionRequest(
        @NotBlank @Size(max = Exhibition.NAME_MAX_LENGTH) String name,
        @Size(max = Exhibition.THEME_CODE_MAX_LENGTH) String themeCode
) {
}
