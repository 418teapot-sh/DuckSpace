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
import com.duckspace.global.auth.JwtTokenProvider;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(request.email())
                .nickname(request.nickname())
                .password(passwordEncoder.encode(request.password()))
                .authProvider(AuthProvider.LOCAL)
                .build();
        userRepository.save(user);

        return issueTokens(user.getId());
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        loginAttemptLimiter.checkAllowed(request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseGet(() -> {
                    loginAttemptLimiter.onFailure(request.email());
                    throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
                });

        if (!user.isLocal()) {
            throw new BusinessException(AuthErrorCode.OAUTH_ONLY_ACCOUNT);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            loginAttemptLimiter.onFailure(request.email());
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        loginAttemptLimiter.onSuccess(request.email());
        return issueTokens(user.getId());
    }

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        if (!jwtTokenProvider.validate(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        refreshTokenRepository.findByUserId(userId)
                .filter(saved -> RefreshTokenHasher.matches(refreshToken, saved.getTokenHash()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        return issueTokens(userId);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (!jwtTokenProvider.validate(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            return;
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        refreshTokenRepository.findByUserId(userId)
                .filter(saved -> RefreshTokenHasher.matches(refreshToken, saved.getTokenHash()))
                .ifPresent(refreshTokenRepository::delete);
    }

    private TokenResponse issueTokens(Long userId) {
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        saveRefreshToken(userId, refreshToken);
        return new TokenResponse(accessToken, refreshToken);
    }

    private void saveRefreshToken(Long userId, String rawToken) {
        String tokenHash = RefreshTokenHasher.hash(rawToken);
        RefreshToken entity = refreshTokenRepository.findByUserId(userId)
                .map(saved -> saved.update(tokenHash))
                .orElse(new RefreshToken(userId, tokenHash));

        refreshTokenRepository.save(entity);
    }
}
