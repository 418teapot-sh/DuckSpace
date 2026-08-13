package com.duckspace.domain.user.controller;

import com.duckspace.domain.user.dto.request.UpdateProfileRequest;
import com.duckspace.domain.user.dto.response.UserProfileResponse;
import com.duckspace.domain.user.service.UserService;
import com.duckspace.global.auth.AuthUser;
import com.duckspace.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "유저", description = "유저 프로필")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "유저 프로필 조회", description = "팔로워/팔로잉 수를 포함합니다.")
    @GetMapping("/{userId}")
    public ApiResponse<UserProfileResponse> getProfile(@PathVariable Long userId) {
        return ApiResponse.success(userService.getProfile(userId));
    }

    @Operation(summary = "내 프로필 조회", description = "로그인한 유저 본인의 프로필입니다.")
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(userService.getProfile(authUser.getUserId()));
    }

    @Operation(summary = "내 프로필 수정", description = "닉네임과 프로필 이미지 URL을 수정합니다.")
    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateMyProfile(@AuthenticationPrincipal AuthUser authUser,
                                                              @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userService.updateProfile(authUser.getUserId(), request));
    }
}
