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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    public Exhibition(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public boolean isOwnedBy(Long userId) {
        return Objects.equals(this.userId, userId);
    }

    public void rename(String name) {
        this.name = name;
    }
}
