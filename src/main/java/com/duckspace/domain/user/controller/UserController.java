package com.duckspace.domain.user.controller;

import com.duckspace.domain.user.dto.request.UpdateProfileRequest;
import com.duckspace.domain.user.dto.response.ProfileImageResponse;
import com.duckspace.domain.user.dto.response.UserProfileResponse;
import com.duckspace.domain.user.service.ProfileImageService;
import com.duckspace.domain.user.service.UserService;
import com.duckspace.global.auth.AuthUser;
import com.duckspace.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "유저", description = "유저 프로필")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ProfileImageService profileImageService;

    @Operation(summary = "유저 프로필 조회", description = "팔로워/팔로잉 수를 포함합니다.")
    @GetMapping("/{userId:[0-9]+}")
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

    @Operation(summary = "프로필 사진 업로드",
            description = """
                    사진을 올리면 즉시 저장하고 내 프로필에 반영한 뒤 URL을 돌려줍니다(후처리 없음).
                    JPG 또는 PNG, 10MB 이하만 받습니다. 기존 사진이 있었다면 교체 후 정리합니다.
                    """)
    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProfileImageResponse> uploadProfileImage(@AuthenticationPrincipal AuthUser authUser,
                                                                  @RequestParam("image") MultipartFile image) {
        String imageUrl = profileImageService.upload(authUser.getUserId(), image);
        return ApiResponse.success(new ProfileImageResponse(imageUrl));
    }
}
