package com.duckspace.global.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토큰 생성·해석 규칙을 고정합니다.
 *
 * <p>여기서 나는 예외는 {@code JwtAuthenticationFilter} 안에서 터지는데, 필터는
 * {@code @RestControllerAdvice} 바깥이라 {@code GlobalExceptionHandler} 가 잡지 못합니다 —
 * 그대로 두면 401 이어야 할 상황이 파싱 불가능한 500 HTML 로 나갑니다.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "test-only-secret-key-do-not-use-in-production-0123456789";
    private static final Long USER_ID = 42L;

    private final JwtTokenProvider provider = new JwtTokenProvider(
            new JwtProperties(SECRET, 30 * 60 * 1000L, 14L * 24 * 60 * 60 * 1000));

    /** 지금 코드가 만들 수 없는 토큰(예: 롤백 전 배포가 발급한 것)을 흉내냅니다. */
    private String tokenWithRole(String rawRole) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(USER_ID))
                .claim("type", "access")
                .claim("role", rawRole)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("액세스 토큰에는 role 이 실리고 리프레시 토큰에는 안 실린다")
    void 토큰_종류별_role_유무() {
        String access = provider.createAccessToken(USER_ID, Role.ADMIN);
        String refresh = provider.createRefreshToken(USER_ID);

        assertThat(provider.getRole(access)).isEqualTo(Role.ADMIN);
        assertThat(provider.getUserId(access)).isEqualTo(USER_ID);
        assertThat(provider.isAccessToken(access)).isTrue();

        assertThat(provider.isRefreshToken(refresh)).isTrue();
        assertThat(provider.getRole(refresh))
                .as("리프레시 토큰에는 role 이 없습니다. 인가 판단은 액세스 토큰으로만 합니다")
                .isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("role 을 안 넘기면 USER 로 발급한다")
    void role_이_null_이어도_터지지_않는다() {
        String access = provider.createAccessToken(USER_ID, null);

        assertThat(provider.getRole(access)).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("모르는 role 값이면 예외 대신 USER 로 떨어뜨린다")
    void 모르는_role_은_USER_로_폴백() {
        // 상수를 추가해 배포했다가 롤백하면, 이미 발급된 토큰에 지금은 없는 값이 남습니다.
        // 여기서 예외가 나면 필터 밖으로 나가 500 이 되고, 그 토큰이 만료될 때까지
        // 해당 사용자는 아무 요청도 못 합니다.
        String token = tokenWithRole("MANAGER");

        assertThat(provider.getRole(token)).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("액세스 토큰 하나로 인증 정보를 한 번에 만든다")
    void 한_번만_파싱해서_인증정보를_만든다() {
        // 예전에는 필터가 validate → isAccessToken → getUserId → getRole 을 차례로 불러서
        // 인증된 요청마다 서명 검증이 네 번씩 돌았습니다.
        String access = provider.createAccessToken(USER_ID, Role.ADMIN);

        AuthUser authUser = provider.parseAccessUser(access).orElseThrow();

        assertThat(authUser.getUserId()).isEqualTo(USER_ID);
        assertThat(authUser.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("리프레시 토큰으로는 인증되지 않는다")
    void 리프레시_토큰은_인증에_못_쓴다() {
        // 서명은 멀쩡하므로 타입을 안 보면 그대로 통과합니다. 리프레시 토큰에는 role 이 없어
        // 전부 USER 로 인증되고, 재발급용 토큰이 사실상 무기한 액세스 토큰이 됩니다.
        String refresh = provider.createRefreshToken(USER_ID);

        assertThat(provider.parseAccessUser(refresh)).isEmpty();
    }

    @Test
    @DisplayName("서명이 다르거나 형식이 깨진 토큰은 예외 없이 비어 있다")
    void 잘못된_토큰은_비어_있다() {
        // 필터는 @RestControllerAdvice 바깥이라, 여기서 예외가 새면 500 HTML 이 나갑니다.
        assertThat(provider.parseAccessUser("이건 토큰이 아닙니다")).isEmpty();
        assertThat(provider.parseAccessUser(tokenWithRole("MANAGER")))
                .as("모르는 role 은 USER 로 떨어질 뿐 인증 자체는 됩니다")
                .isPresent();
    }

    @Test
    @DisplayName("서명이 다른 토큰은 검증에서 걸러진다")
    void 위조_토큰은_거부() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-secret-key-that-is-long-enough-0123456789".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .subject("1")
                .claim("type", "access")
                .claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        assertThat(provider.validate(forged)).isFalse();
    }
}
