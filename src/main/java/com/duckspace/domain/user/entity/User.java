package com.duckspace.domain.user.entity;

import com.duckspace.global.auth.Role;
import com.duckspace.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "email", nullable = false, updatable = false, unique = true)
    private String email;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    /** 폼 로그인(LOCAL) 사용자만 값을 가짐. 구글 로그인(GOOGLE) 사용자는 null. */
    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider authProvider;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false)
    private Role role;

    @Builder
    private User(String email, String nickname, String password, AuthProvider authProvider, Role role) {
        this.email = email;
        this.nickname = nickname;
        this.password = password;
        this.authProvider = authProvider;
        this.role = role == null ? Role.USER : role;
    }

    public User updateNickname(String nickname) {
        this.nickname = nickname;
        return this;
    }

    public boolean isLocal() {
        return authProvider == AuthProvider.LOCAL && password != null;
    }
}
