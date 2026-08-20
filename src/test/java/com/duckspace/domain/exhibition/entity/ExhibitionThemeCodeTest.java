package com.duckspace.domain.exhibition.entity;

import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 배경 테마 코드가 프론트가 아는 값인지 검사합니다.
 *
 * <p>목록에 없는 값이 저장되면 서버는 200 을 주는데 <b>화면에는 배경이 안 그려집니다.</b>
 * 에러도 로그도 없어서, 사용자가 "배경이 사라졌다" 고 할 때까지 아무도 모릅니다.
 * 저장 시점에 400 으로 막는 게 이 검사의 목적입니다.
 */
class ExhibitionThemeCodeTest {

    private static final Long OWNER = 1L;

    @Test
    @DisplayName("프론트가 아는 7개 코드는 모두 통과한다")
    void 유효한_코드는_통과() {
        // 여기서 막히면 사용자가 고른 배경이 저장되지 않습니다.
        for (String code : Exhibition.THEME_CODES) {
            assertThatCode(() -> new Exhibition(OWNER, "장식장", code))
                    .as("테마 %s", code)
                    .doesNotThrowAnyException();
        }

        assertThat(Exhibition.THEME_CODES)
                .as("프론트 displayThemes.js 의 DISPLAY_THEMES 와 1:1 이어야 합니다")
                .containsExactlyInAnyOrder(
                        "BASIC", "PURPLE", "GREEN", "YELLOW", "ORANGE", "PINK", "BLUE");
    }

    @Test
    @DisplayName("목록에 없는 코드는 생성에서 400 으로 막힌다")
    void 모르는_코드는_생성에서_거부() {
        assertThatThrownBy(() -> new Exhibition(OWNER, "장식장", "RAINBOW"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ExhibitionErrorCode.INVALID_THEME_CODE);
    }

    @Test
    @DisplayName("목록에 없는 코드는 수정에서도 막히고, 기존 테마가 그대로 남는다")
    void 모르는_코드는_수정에서_거부() {
        Exhibition exhibition = new Exhibition(OWNER, "장식장", "PINK");

        assertThatThrownBy(() -> exhibition.changeTheme("RAINBOW"))
                .isInstanceOf(BusinessException.class);

        assertThat(exhibition.getThemeCode())
                .as("거부됐으면 원래 값이 남아야 합니다")
                .isEqualTo("PINK");
    }

    @Test
    @DisplayName("대소문자가 다르면 다른 값이다")
    void 소문자는_거부() {
        // 프론트는 대문자 코드로 배경을 찾습니다. 관대하게 받아주면 DB 에 두 표기가 섞이고,
        // 그때부터는 어느 쪽이 맞는지 아무도 모릅니다.
        assertThatThrownBy(() -> new Exhibition(OWNER, "장식장", "basic"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("비우면 기본 테마가 되고, 수정에서 비우면 기존 값을 유지한다")
    void 빈_값_처리는_그대로() {
        // 이 동작은 바꾸지 않았습니다 — 프론트가 테마를 안 보내는 화면이 있습니다.
        assertThat(new Exhibition(OWNER, "장식장", null).getThemeCode())
                .isEqualTo(Exhibition.DEFAULT_THEME_CODE);
        assertThat(new Exhibition(OWNER, "장식장", "  ").getThemeCode())
                .isEqualTo(Exhibition.DEFAULT_THEME_CODE);

        Exhibition exhibition = new Exhibition(OWNER, "장식장", "BLUE");
        exhibition.changeTheme(null);
        exhibition.changeTheme("   ");
        assertThat(exhibition.getThemeCode()).isEqualTo("BLUE");
    }
}
