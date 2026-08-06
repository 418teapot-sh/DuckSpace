package com.duckspace.domain.auth.controller;

import com.duckspace.domain.auth.dto.LoginRequest;
import com.duckspace.domain.auth.dto.RefreshRequest;
import com.duckspace.domain.auth.dto.SignupRequest;
import com.duckspace.domain.auth.dto.TokenResponse;
import com.duckspace.domain.auth.service.AuthService;
import com.duckspace.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ApiResponse<TokenResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.reissue(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.noContent();
    }
}
