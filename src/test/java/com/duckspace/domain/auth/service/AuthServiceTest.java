package com.duckspace.domain.auth.service;

import com.duckspace.domain.auth.dto.LoginRequest;
import com.duckspace.domain.auth.dto.SignupRequest;
import com.duckspace.domain.auth.dto.TokenResponse;
import com.duckspace.domain.auth.entity.RefreshToken;
import com.duckspace.domain.auth.exception.AuthErrorCode;
import com.duckspace.domain.auth.repository.RefreshTokenRepository;
import com.duckspace.domain.user.entity.AuthProvider;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.auth.JwtProperties;
import com.duckspace.global.auth.JwtTokenProvider;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "test-secret-key-must-be-long-enough-for-hs256-0123456789";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private JwtTokenProvider jwtTokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(new JwtProperties(SECRET, 1000 * 60 * 30L, 1000 * 60 * 60 * 24 * 14L));
        authService = new AuthService(jwtTokenProvider, refreshTokenRepository, userRepository, passwordEncoder,
                new LoginAttemptLimiter());
    }

    private User localUser(String email, String encodedPassword) {
        User user = User.builder()
                .email(email)
                .nickname("테스트유저")
                .password(encodedPassword)
                .authProvider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    @Nested
    @DisplayName("signup 메서드는")
    class Signup {

        @Test
        void 신규_이메일이면_가입하고_토큰을_발급한다() {
            given(userRepository.findByEmail("test@duckspace.com")).willReturn(Optional.empty());
            given(passwordEncoder.encode("password1234")).willReturn("encoded");
            given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> {
                User user = invocation.getArgument(0);
                ReflectionTestUtils.setField(user, "id", 1L);
                return user;
            });

            TokenResponse response = authService.signup(
                    new SignupRequest("test@duckspace.com", "password1234", "닉네임"));

            assertThat(response.accessToken()).isNotBlank();
            assertThat(response.refreshToken()).isNotBlank();
        }

        @Test
        void 이미_가입된_이메일이면_예외() {
            given(userRepository.findByEmail("test@duckspace.com"))
                    .willReturn(Optional.of(localUser("test@duckspace.com", "encoded")));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.signup(new SignupRequest("test@duckspace.com", "password1234", "닉네임")));

            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        @Test
        void 이메일_대소문자를_구분하지_않도록_소문자로_정규화한다() {
            SignupRequest request = new SignupRequest("Test@DuckSpace.com", "password1234", "닉네임");

            assertThat(request.email()).isEqualTo("test@duckspace.com");
        }
    }

    @Nested
    @DisplayName("login 메서드는")
    class Login {

        @Test
        void 비밀번호가_맞으면_토큰을_발급한다() {
            User user = localUser("test@duckspace.com", "encoded");
            given(userRepository.findByEmail("test@duckspace.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("password1234", "encoded")).willReturn(true);

            TokenResponse response = authService.login(new LoginRequest("test@duckspace.com", "password1234"));

            assertThat(response.accessToken()).isNotBlank();
            assertThat(response.refreshToken()).isNotBlank();
        }

        @Test
        void 존재하지_않는_이메일이면_예외() {
            given(userRepository.findByEmail("unknown@duckspace.com")).willReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.login(new LoginRequest("unknown@duckspace.com", "password1234")));

            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        void 비밀번호가_틀리면_예외() {
            User user = localUser("test@duckspace.com", "encoded");
            given(userRepository.findByEmail("test@duckspace.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrong-password", "encoded")).willReturn(false);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.login(new LoginRequest("test@duckspace.com", "wrong-password")));

            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        void 구글_계정으로_가입된_이메일이면_예외() {
            User googleUser = User.builder()
                    .email("test@duckspace.com")
                    .nickname("구글유저")
                    .authProvider(AuthProvider.GOOGLE)
                    .build();
            given(userRepository.findByEmail("test@duckspace.com")).willReturn(Optional.of(googleUser));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.login(new LoginRequest("test@duckspace.com", "password1234")));

            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        void 비밀번호를_다섯_번_틀리면_그_다음_시도는_잠긴다() {
            User user = localUser("test@duckspace.com", "encoded");
            given(userRepository.findByEmail("test@duckspace.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrong-password", "encoded")).willReturn(false);

            for (int i = 0; i < 5; i++) {
                assertThrows(BusinessException.class,
                        () -> authService.login(new LoginRequest("test@duckspace.com", "wrong-password")));
            }

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.login(new LoginRequest("test@duckspace.com", "wrong-password")));
            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
    }

    @Nested
    @DisplayName("reissue 메서드는")
    class Reissue {

        @Test
        void 저장된_토큰과_일치하면_새_토큰을_발급한다() {
            String refreshToken = jwtTokenProvider.createRefreshToken(1L);
            RefreshToken saved = new RefreshToken(1L, RefreshTokenHasher.hash(refreshToken));
            given(userRepository.existsById(1L)).willReturn(true);
            given(refreshTokenRepository.findByUserId(1L)).willReturn(Optional.of(saved));

            TokenResponse response = authService.reissue(refreshToken);

            assertThat(response.accessToken()).isNotBlank();
            assertThat(response.refreshToken()).isNotBlank();
        }

        @Test
        void 액세스_토큰으로_시도하면_예외() {
            String accessToken = jwtTokenProvider.createAccessToken(1L);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.reissue(accessToken));

            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        @Test
        void 형식이_잘못된_토큰이면_예외() {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.reissue("not-a-jwt"));

            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        @Test
        void 저장된_해시와_일치하지_않으면_예외() {
            String refreshToken = jwtTokenProvider.createRefreshToken(1L);
            RefreshToken saved = new RefreshToken(1L, RefreshTokenHasher.hash("다른-토큰"));
            given(userRepository.existsById(1L)).willReturn(true);
            given(refreshTokenRepository.findByUserId(1L)).willReturn(Optional.of(saved));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.reissue(refreshToken));

            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    @Nested
    @DisplayName("logout 메서드는")
    class Logout {

        @Test
        void 저장된_토큰과_일치하면_삭제한다() {
            String refreshToken = jwtTokenProvider.createRefreshToken(1L);
            RefreshToken saved = new RefreshToken(1L, RefreshTokenHasher.hash(refreshToken));
            given(refreshTokenRepository.findByUserId(1L)).willReturn(Optional.of(saved));

            authService.logout(refreshToken);

            verify(refreshTokenRepository, times(1)).delete(saved);
        }

        @Test
        void 유효하지_않은_토큰이면_아무일도_하지_않는다() {
            authService.logout("not-a-jwt");

            verify(refreshTokenRepository, never()).delete(any());
        }
    }
}
