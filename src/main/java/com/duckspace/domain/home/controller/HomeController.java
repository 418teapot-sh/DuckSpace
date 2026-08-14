package com.duckspace.domain.home.controller;

import com.duckspace.domain.home.dto.response.HomeResponse;
import com.duckspace.domain.home.service.HomeService;
import com.duckspace.global.auth.AuthUser;
import com.duckspace.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;

    // 비로그인 요청도 허용됩니다(anonymous 는 Security 상 "인증됨"으로 통과). 그때 authUser 는 null 입니다.
    @GetMapping
    public ApiResponse<HomeResponse> getHome(@AuthenticationPrincipal AuthUser authUser) {
        Long viewerId = authUser == null ? null : authUser.getUserId();
        return ApiResponse.success(homeService.getHome(viewerId));
    }
}