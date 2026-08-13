package com.duckspace.domain.auth.controller;

import com.duckspace.domain.auth.dto.LoginRequest;
import com.duckspace.domain.auth.dto.RefreshRequest;
import com.duckspace.domain.auth.dto.SignupRequest;
import com.duckspace.domain.auth.dto.TokenResponse;
import com.duckspace.domain.auth.service.AuthService;
import com.duckspace.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "회원가입 · 로그인 · 토큰 재발급 · 로그아웃")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입",
            description = """
                    이메일이 이미 가입되어 있으면 409(EMAIL_ALREADY_EXISTS)입니다.
                    비밀번호는 8~64자, 닉네임은 최대 30자입니다.
                    가입과 동시에 로그인 처리되어 응답에 액세스·리프레시 토큰이 함께 나갑니다.
                    """)
    @PostMapping("/signup")
    public ApiResponse<TokenResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @Operation(summary = "로그인",
            description = """
                    이메일 하나당 15분에 5회까지만 시도할 수 있습니다. 초과하면 429(TOO_MANY_LOGIN_ATTEMPTS)이고,
                    성공하면 시도 횟수가 초기화됩니다.
                    이메일이 없거나 비밀번호가 틀려도 어느 쪽이 문제인지 구분하지 않고 동일하게
                    401(INVALID_CREDENTIALS)을 반환합니다(가입 여부 유추 방지).
                    """)
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(summary = "토큰 재발급",
            description = """
                    리프레시 토큰으로 액세스·리프레시 토큰을 둘 다 새로 발급합니다(리프레시 토큰도 매번 회전).
                    유효하지 않거나 서버에 저장된 값과 다르면 401(INVALID_REFRESH_TOKEN)입니다.
                    """)
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.reissue(request.refreshToken()));
    }

    @Operation(summary = "로그아웃",
            description = """
                    서버에 저장된 리프레시 토큰을 삭제합니다.
                    토큰이 이미 무효하거나 없어도 에러 없이 성공 처리됩니다(멱등).
                    """)
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.noContent();
    }
}
