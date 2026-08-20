package com.duckspace.domain.exhibition.dto.request;

import com.duckspace.domain.exhibition.entity.Exhibition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param themeCode 배경 테마. 비우면 기본 테마({@code BASIC})가 적용됩니다.
 *                  쓸 수 있는 값은 {@code BASIC} · {@code PURPLE} · {@code GREEN} ·
 *                  {@code YELLOW} · {@code ORANGE} · {@code PINK} · {@code BLUE} 입니다
 *                  ({@link Exhibition#THEME_CODES}). 그 외에는 400
 *                  {@code INVALID_THEME_CODE} 입니다.
 */
public record CreateExhibitionRequest(
        @NotBlank @Size(max = Exhibition.NAME_MAX_LENGTH) String name,
        @Size(max = Exhibition.THEME_CODE_MAX_LENGTH) String themeCode
) {
}
