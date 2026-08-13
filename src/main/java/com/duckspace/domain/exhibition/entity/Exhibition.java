package com.duckspace.domain.exhibition.entity;

import com.duckspace.global.entity.BaseTimeEntity;
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
        this.themeCode = (themeCode == null || themeCode.isBlank()) ? DEFAULT_THEME_CODE : themeCode;
    }

    public boolean isOwnedBy(Long userId) {
        return Objects.equals(this.userId, userId);
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeTheme(String themeCode) {
        if (themeCode != null && !themeCode.isBlank()) {
            this.themeCode = themeCode;
        }
    }
}
