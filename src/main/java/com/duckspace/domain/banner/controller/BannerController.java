package com.duckspace.domain.banner.controller;


import com.duckspace.domain.banner.dto.response.BannerListResponse;
import com.duckspace.domain.banner.service.BannerService;
import com.duckspace.global.response.ApiResponse;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.duckspace.domain.banner.dto.response.BannerResponse;
import com.duckspace.domain.banner.dto.request.BannerCreateRequest;
import com.duckspace.domain.banner.dto.request.BannerUpdateRequest;


import java.util.List;

@RestController
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @GetMapping("/api/banners")
    public ApiResponse<BannerListResponse> getActiveBanners() {
        return ApiResponse.success(bannerService.getActiveBanners());
    }


    //주석 처리는 혹시나 관리자용을 만든다면 필요해서 냅둡니다
    /*    @GetMapping("/api/admin/banners")
    public ApiResponse<List<BannerResponse>> getAllBanners() {
        return ApiResponse.success(bannerService.getAllBannersForAdmin());
    }
    */

    /*
    @PostMapping("/api/admin/banners")
    public ApiResponse<BannerResponse> createBanner(@Valid @RequestBody BannerCreateRequest request) {
        return ApiResponse.success(bannerService.createBanner(request));
    }

    @PatchMapping("/api/admin/banners/{bannerId}")
    public ApiResponse<BannerResponse> updateBanner(@PathVariable Long bannerId,
                                                      @Valid @RequestBody BannerUpdateRequest request) {
        return ApiResponse.success(bannerService.updateBanner(bannerId, request));
    }

    @DeleteMapping("/api/admin/banners/{bannerId}")
    public ApiResponse<Void> deleteBanner(@PathVariable Long bannerId) {
        bannerService.deleteBanner(bannerId);
        return ApiResponse.noContent();
    }
    */
}