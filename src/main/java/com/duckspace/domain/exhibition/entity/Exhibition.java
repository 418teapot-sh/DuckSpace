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
 * 장식장. 고정된 배경(책장·협탁 등) 위에 슬롯이 있고, 슬롯마다 굿즈가 하나씩 놓입니다.
 *
 * <p>슬롯의 화면 좌표는 프론트가 고정으로 관리하므로 서버는 슬롯 식별자만 압니다.
 * ({@link ExhibitionItem#getSlotId()})
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
