package com.duckspace.global.auth.oauth;

import com.duckspace.domain.user.User;
import com.duckspace.domain.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Google 로그인 성공 시 사용자를 조회해 1회용 코드만 발급하고 프론트엔드로 리다이렉트한다.
 * 실제 access/refresh 토큰 발급은 {@code /api/auth/token/exchange}에서 이뤄진다.
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AuthorizationCodeStore authorizationCodeStore;
    private final OAuth2AuthorizationRequestBasedOnCookieRepository authorizationRequestRepository;

    @Value("${app.oauth2.authorized-redirect-uri}")
    private String authorizedRedirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = (String) oAuth2User.getAttributes().get("email");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("OAuth2 로그인 이후에는 사용자가 존재해야 합니다: " + email));

        String code = authorizationCodeStore.createCode(user.getId());

        clearAuthenticationAttributes(request, response);

        String targetUrl = UriComponentsBuilder.fromUriString(authorizedRedirectUri)
                .queryParam("code", code)
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private void clearAuthenticationAttributes(HttpServletRequest request, HttpServletResponse response) {
        super.clearAuthenticationAttributes(request);
        authorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
    }
}
