package com.duckspace.global.auth.oauth;

import com.duckspace.global.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * 세션을 쓰지 않는 stateless 서버이므로 OAuth2 authorization request(state)를
 * 쿠키에 담아 Google 리다이렉트 왕복 사이에 보관한다.
 *
 * <p>별도 {@code @Component}로 두는 이유: {@code SecurityConfig}의 {@code @Bean}으로 만들면
 * {@code SecurityConfig -> OAuth2SuccessHandler -> (이 빈) -> SecurityConfig 인스턴스}로 순환 참조가 생긴다.
 */
@Component
public class OAuth2AuthorizationRequestBasedOnCookieRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";

    private static final int COOKIE_EXPIRE_SECONDS = 180;

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        return this.loadAuthorizationRequest(request);
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        return CookieUtil.deserialize(cookie, OAuth2AuthorizationRequest.class);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequestCookies(request, response);
            return;
        }
        CookieUtil.addCookie(response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
                CookieUtil.serialize(authorizationRequest), COOKIE_EXPIRE_SECONDS);
    }

    public void removeAuthorizationRequestCookies(HttpServletRequest request, HttpServletResponse response) {
        CookieUtil.deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
    }
}
