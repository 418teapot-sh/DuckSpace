package com.duckspace.domain.auth.entity;

import com.duckspace.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** 원본 토큰이 아니라 RefreshTokenHasher로 해시한 값. DB 유출 시에도 토큰이 그대로 쓰이지 않도록 한다. */
    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    public RefreshToken(Long userId, String tokenHash) {
        this.userId = userId;
        this.tokenHash = tokenHash;
    }

    public RefreshToken update(String newTokenHash) {
        this.tokenHash = newTokenHash;
        return this;
    }
}
