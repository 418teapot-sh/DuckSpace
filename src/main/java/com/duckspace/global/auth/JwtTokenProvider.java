package com.duckspace.global.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ROLE_CLAIM = "role";
    private static final String ACCESS_TOKEN = "access";
    private static final String REFRESH_TOKEN = "refresh";

    private final SecretKey key;
    private final long accessTokenValidity;
    private final long refreshTokenValidity;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = properties.accessTokenValidity();
        this.refreshTokenValidity = properties.refreshTokenValidity();
    }

    /** {@code role} 이 null 이면 {@link Role#USER} 로 봅니다. */
    public String createAccessToken(Long userId, Role role) {
        return createToken(userId, ACCESS_TOKEN, accessTokenValidity, role == null ? Role.USER : role);
    }

    public String createRefreshToken(Long userId) {
        // 리프레시 토큰에는 role 을 싣지 않습니다. 인가 판단은 액세스 토큰으로만 합니다.
        return createToken(userId, REFRESH_TOKEN, refreshTokenValidity, null);
    }

    /**
     * 두 토큰이 <b>같은 경로</b>로 만들어지도록 한 곳에 모았습니다. 예전에는 액세스 토큰이
     * 이 메서드를 안 거치고 본문을 복제하고 있어서, 토큰 생성 규칙을 여기서 고치면
     * 리프레시 토큰에만 반영되는 상태였습니다.
     */
    private String createToken(Long userId, String tokenType, long validityMillis, Role role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMillis);

        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(now)
                .expiration(expiry);
        if (role != null) {
            builder.claim(ROLE_CLAIM, role.name());
        }
        return builder.signWith(key).compact();
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    /**
     * 액세스 토큰에만 들어있는 claim 입니다. 리프레시 토큰에는 role 이 없습니다.
     * role 배포 이전에 발급된 기존 액세스 토큰에는 claim 자체가 없을 수 있어 USER 로 폴백합니다.
     */
    public Role getRole(String token) {
        String role = parseClaims(token).get(ROLE_CLAIM, String.class);
        if (role == null) {
            return Role.USER;
        }
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            // 지금은 없는 상수입니다 — 롤백 직후 이전 배포가 발급한 토큰이 대표적입니다.
            // null 과 같은 이유로 USER 로 떨어뜨립니다. 여기서 예외를 내면 필터 밖으로
            // 나가 500 이 되고, 그 토큰이 만료될 때까지 그 사용자는 아무것도 못 합니다.
            log.warn("모르는 role claim 이라 USER 로 처리합니다: {}", role);
            return Role.USER;
        }
    }

    public boolean isAccessToken(String token) {
        return ACCESS_TOKEN.equals(getTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return REFRESH_TOKEN.equals(getTokenType(token));
    }

    private String getTokenType(String token) {
        try {
            return parseClaims(token).get(TOKEN_TYPE_CLAIM, String.class);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("만료된 토큰입니다.");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("유효하지 않은 토큰입니다: {}", e.getMessage());
        }
        return false;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
