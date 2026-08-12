package com.duckspace.domain.user.controller;

import com.duckspace.domain.user.dto.response.FollowUserResponse;
import com.duckspace.domain.user.service.FollowService;
import com.duckspace.global.auth.AuthUser;
import com.duckspace.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "팔로우", description = "팔로우/팔로워")
@RestController
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @Operation(summary = "팔로우")
    @PostMapping("/api/users/{userId}/follow")
    public ApiResponse<Void> follow(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long userId) {
        followService.follow(authUser.getUserId(), userId);
        return ApiResponse.noContent();
    }

    @Operation(summary = "언팔로우")
    @DeleteMapping("/api/users/{userId}/follow")
    public ApiResponse<Void> unfollow(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long userId) {
        followService.unfollow(authUser.getUserId(), userId);
        return ApiResponse.noContent();
    }

    @Operation(summary = "팔로워 목록", description = "cursor를 비우면 최신부터, 값을 주면 그보다 오래된 팔로워부터 내려줍니다(마지막으로 받은 id를 cursor에 넣으면 됨).")
    @GetMapping("/api/users/{userId}/followers")
    public ApiResponse<List<FollowUserResponse>> followers(@PathVariable Long userId,
                                                             @RequestParam(required = false) Long cursor,
                                                             @RequestParam(required = false) Integer size) {
        return ApiResponse.success(followService.getFollowers(userId, cursor, size));
    }

    @Operation(summary = "팔로잉 목록", description = "cursor를 비우면 최신부터, 값을 주면 그보다 오래된 팔로잉부터 내려줍니다(마지막으로 받은 id를 cursor에 넣으면 됨).")
    @GetMapping("/api/users/{userId}/following")
    public ApiResponse<List<FollowUserResponse>> following(@PathVariable Long userId,
                                                             @RequestParam(required = false) Long cursor,
                                                             @RequestParam(required = false) Integer size) {
        return ApiResponse.success(followService.getFollowing(userId, cursor, size));
    }
}
