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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final RefreshTokenWriter refreshTokenWriter;

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

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        loginAttemptLimiter.checkAllowed(request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!user.isLocal() || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        loginAttemptLimiter.onSuccess(request.email());
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        if (!jwtTokenProvider.validate(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        // role 은 리프레시 토큰에 없으므로(발급 시점 이후 바뀌었을 수 있음) DB에서 다시 조회합니다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        refreshTokenRepository.findByUserId(userId)
                .filter(saved -> RefreshTokenHasher.matches(refreshToken, saved.getTokenHash()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        return issueTokens(user);
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

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        saveRefreshToken(user.getId(), refreshToken);
        return new TokenResponse(accessToken, refreshToken);
    }

    /**
     * 기존 행이 있으면 같은 트랜잭션에서 그냥 갱신합니다(update만이라 유니크 제약에 안 걸림).
     * 없으면 {@link RefreshTokenWriter}로 INSERT를 별도 트랜잭션에 위임해서, 동시 요청으로
     * 유니크 제약에 걸려도 이 트랜잭션(과 세션)은 오염되지 않게 하고 재조회로 복구합니다.
     */
    private void saveRefreshToken(Long userId, String rawToken) {
        String tokenHash = RefreshTokenHasher.hash(rawToken);
        Optional<RefreshToken> existing = refreshTokenRepository.findByUserId(userId);

        if (existing.isPresent()) {
            existing.get().update(tokenHash);
            return;
        }

        try {
            refreshTokenWriter.insert(userId, tokenHash);
        } catch (DataIntegrityViolationException e) {
            RefreshToken concurrentlyInserted = refreshTokenRepository.findByUserId(userId)
                    .orElseThrow(() -> e);
            concurrentlyInserted.update(tokenHash);
        }
    }
}
