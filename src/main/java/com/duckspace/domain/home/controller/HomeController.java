package com.duckspace.domain.home.controller;

import com.duckspace.domain.home.dto.response.HomeResponse;
import com.duckspace.domain.home.service.HomeService;
import com.duckspace.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    public ApiResponse<HomeResponse> getHome() {
        return ApiResponse.success(homeService.getHome());
    }
}