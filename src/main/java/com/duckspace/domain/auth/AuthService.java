package com.duckspace.domain.auth;

import com.duckspace.domain.auth.dto.LoginRequest;
import com.duckspace.domain.auth.dto.SignupRequest;
import com.duckspace.domain.auth.dto.TokenResponse;
import com.duckspace.domain.user.AuthProvider;
import com.duckspace.domain.user.User;
import com.duckspace.domain.user.UserRepository;
import com.duckspace.global.auth.JwtTokenProvider;
import com.duckspace.global.auth.oauth.AuthorizationCodeStore;
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
    private final AuthorizationCodeStore authorizationCodeStore;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!user.isLocal()) {
            throw new BusinessException(AuthErrorCode.OAUTH_ONLY_ACCOUNT);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(user.getId());
    }

    @Transactional
    public TokenResponse exchange(String code) {
        Long userId = authorizationCodeStore.consumeCode(code)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CODE));

        return issueTokens(userId);
    }

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        if (!jwtTokenProvider.validate(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        refreshTokenRepository.findByUserId(userId)
                .filter(saved -> saved.getToken().equals(refreshToken))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        return issueTokens(userId);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }

    private TokenResponse issueTokens(Long userId) {
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        saveRefreshToken(userId, refreshToken);
        return new TokenResponse(accessToken, refreshToken);
    }

    private void saveRefreshToken(Long userId, String token) {
        RefreshToken entity = refreshTokenRepository.findByUserId(userId)
                .map(saved -> saved.update(token))
                .orElse(new RefreshToken(userId, token));

        refreshTokenRepository.save(entity);
    }
}
