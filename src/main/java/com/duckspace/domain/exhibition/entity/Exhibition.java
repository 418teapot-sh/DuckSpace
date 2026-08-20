package com.duckspace.domain.exhibition.entity;

import com.duckspace.global.entity.BaseTimeEntity;
import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.Set;

/**
 * 장식장. 배경 위에 굿즈를 자유롭게 배치합니다.
 *
 * <p>배경 이미지는 프론트가 {@link #getThemeCode()} 로 찾고, 굿즈의 위치·크기는
 * {@link ExhibitionItem} 이 배경 대비 비율로 들고 있습니다.
 */
@Entity
@Table(
        name = "exhibition",
        // 내 장식장 목록 조회가 user_id 기준이라 인덱스를 둡니다.
        indexes = @Index(name = "idx_exhibition_user", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exhibition extends BaseTimeEntity {

    public static final int NAME_MAX_LENGTH = 30;
    public static final int THEME_CODE_MAX_LENGTH = 30;

    /** 테마를 고르지 않았을 때 쓰는 기본 배경. */
    public static final String DEFAULT_THEME_CODE = "BASIC";

    /**
     * 쓸 수 있는 배경 테마 코드.
     *
     * <p><b>프론트가 이 코드로 배경 이미지를 고릅니다.</b> 목록에 없는 값이 저장되면 서버는
     * 200 을 주는데 화면에는 배경이 안 그려집니다 — 에러도 로그도 없어서, 사용자가
     * "배경이 사라졌다" 고 할 때까지 아무도 모릅니다. 그래서 저장 시점에 막습니다.
     *
     * <p>값은 프론트 저장소 {@code src/components/displayThemes.js} 의 {@code DISPLAY_THEMES}
     * 와 1:1 입니다(2026-08-20 확인). 배경 이미지를 추가하려면 <b>양쪽을 같이</b> 고쳐야 합니다 —
     * 여기만 늘리면 프론트가 못 그리고, 프론트만 늘리면 여기서 400 이 납니다.
     *
     * <p>선언 순서는 프론트의 배경 순서와 같습니다(기본 → 보라 → 초록 → 노랑 → 주황 → 분홍 → 파랑).
     */
    public static final Set<String> THEME_CODES = Set.of(
            DEFAULT_THEME_CODE, "PURPLE", "GREEN", "YELLOW", "ORANGE", "PINK", "BLUE");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    /**
     * 배경 테마 식별자. 실제 배경 이미지는 프론트가 코드별로 갖고 있습니다.
     *
     * <p>유료 테마 구매·정산은 이번 범위가 아니라 프리셋 코드만 저장합니다.
     * 나중에 판매를 붙이면 이 값이 구매한 테마를 가리키게 됩니다.
     */
    @Column(name = "theme_code", nullable = false, length = THEME_CODE_MAX_LENGTH)
    private String themeCode;

    public Exhibition(Long userId, String name, String themeCode) {
        this.userId = userId;
        this.name = name;
        this.themeCode = (themeCode == null || themeCode.isBlank())
                ? DEFAULT_THEME_CODE
                : validated(themeCode);
    }

    /**
     * 목록에 없는 테마 코드를 막습니다.
     *
     * <p>생성과 수정이 모두 이 한 곳을 지나갑니다. DTO 에 검증을 걸면 시드 스크립트나
     * 테스트처럼 컨트롤러를 안 타는 경로가 빠져나가서, 엔티티에 뒀습니다.
     */
    private static String validated(String themeCode) {
        if (!THEME_CODES.contains(themeCode)) {
            throw new BusinessException(ExhibitionErrorCode.INVALID_THEME_CODE);
        }
        return themeCode;
    }

    public boolean isOwnedBy(Long userId) {
        return Objects.equals(this.userId, userId);
    }

    public void rename(String name) {
        this.name = name;
    }

    /** 비우면 기존 테마를 그대로 둡니다. 목록에 없는 코드는 거부합니다. */
    public void changeTheme(String themeCode) {
        if (themeCode != null && !themeCode.isBlank()) {
            this.themeCode = validated(themeCode);
        }
    }
}
