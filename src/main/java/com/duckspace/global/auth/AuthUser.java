package com.duckspace.global.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 인증된 사용자 정보. JWT 에서 꺼낸 userId 만 담습니다. (DB 조회 없음)
 *
 * <p>컨트롤러에서 로그인한 유저 id 를 꺼내는 방법:
 * <pre>{@code
 * @GetMapping("/posts/mine")
 * public ApiResponse<...> myPosts(@AuthenticationPrincipal AuthUser authUser) {
 *     Long userId = authUser.getUserId();
 * }
 * }</pre>
 */
public record AuthUser(Long userId) implements UserDetails {

    public Long getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return String.valueOf(userId);
    }
}
