package com.duckspace.domain.exhibition.controller;

import com.duckspace.domain.exhibition.dto.request.ExhibitionCreateRequest;
import com.duckspace.domain.exhibition.dto.request.ExhibitionUpdateRequest;
import com.duckspace.domain.exhibition.dto.response.ExhibitionListResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionResponse;
import com.duckspace.domain.exhibition.service.ExhibitionService;
import com.duckspace.global.auth.AuthUser;
import com.duckspace.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExhibitionController {

    /** 썸네일이 S3/CDN으로 옮겨가도 그대로 쓸 수 있는 짧은 브라우저 캐시 정책 */
    private static final String THUMBNAIL_CACHE_CONTROL = "max-age=300";

    private final ExhibitionService exhibitionService;

    @GetMapping("/api/exhibitions")
    public ApiResponse<ExhibitionListResponse> getExhibitions(
            @PageableDefault(size = 12) Pageable pageable,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", THUMBNAIL_CACHE_CONTROL);
        return ApiResponse.success(exhibitionService.getExhibitions(pageable));
    }

    @GetMapping("/api/exhibitions/{exhibitionId}")
    public ApiResponse<ExhibitionResponse> getExhibition(
            @PathVariable Long exhibitionId,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", THUMBNAIL_CACHE_CONTROL);
        return ApiResponse.success(exhibitionService.getExhibition(exhibitionId));
    }

    @PostMapping("/api/exhibitions/{exhibitionId}/like")
    public ApiResponse<Void> toggleLike(
            @PathVariable Long exhibitionId,
            @AuthenticationPrincipal AuthUser authUser) {
        exhibitionService.toggleLike(exhibitionId, authUser.getUserId());
        return ApiResponse.noContent();
    }

    @PostMapping("/api/admin/exhibitions")
    public ApiResponse<ExhibitionResponse> createExhibition(@Valid @RequestBody ExhibitionCreateRequest request) {
        return ApiResponse.success(exhibitionService.createExhibition(request));
    }

    @PatchMapping("/api/admin/exhibitions/{exhibitionId}")
    public ApiResponse<ExhibitionResponse> updateExhibition(
            @PathVariable Long exhibitionId,
            @Valid @RequestBody ExhibitionUpdateRequest request) {
        return ApiResponse.success(exhibitionService.updateExhibition(exhibitionId, request));
    }

    @DeleteMapping("/api/admin/exhibitions/{exhibitionId}")
    public ApiResponse<Void> deleteExhibition(@PathVariable Long exhibitionId) {
        exhibitionService.deleteExhibition(exhibitionId);
        return ApiResponse.noContent();
    }
}