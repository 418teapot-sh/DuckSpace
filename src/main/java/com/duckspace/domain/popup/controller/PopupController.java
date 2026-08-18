package com.duckspace.domain.popup.controller;

import com.duckspace.domain.popup.dto.request.PopupCreateRequest;
import com.duckspace.domain.popup.dto.request.PopupUpdateRequest;
import com.duckspace.domain.popup.dto.response.PopupResponse;
import com.duckspace.domain.popup.dto.response.PopupSummaryResponse;
import com.duckspace.domain.popup.service.PopupLikeService;
import com.duckspace.domain.popup.service.PopupService;
import com.duckspace.global.auth.AuthUser;
import com.duckspace.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PopupController {

    private final PopupService popupService;
    private final PopupLikeService popupLikeService;

    @GetMapping("/api/popups")
    public ApiResponse<List<PopupSummaryResponse>> getPopups(@AuthenticationPrincipal AuthUser authUser) {
        Long viewerId = authUser == null ? null : authUser.getUserId();
        return ApiResponse.success(popupService.getPopups(viewerId));
    }

    @GetMapping("/api/popups/{popupId}")
    public ApiResponse<PopupResponse> getPopup(@PathVariable Long popupId,
                                                @AuthenticationPrincipal AuthUser authUser) {
        Long viewerId = authUser == null ? null : authUser.getUserId();
        return ApiResponse.success(popupService.getPopup(popupId, viewerId));
    }

    @GetMapping("/api/popups/likes")
    public ApiResponse<List<PopupSummaryResponse>> getLikedPopups(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(popupLikeService.getLikedPopups(authUser.getUserId()));
    }

    @PostMapping("/api/popups/{popupId}/like")
    public ApiResponse<Void> like(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long popupId) {
        popupLikeService.like(authUser.getUserId(), popupId);
        return ApiResponse.noContent();
    }

    @DeleteMapping("/api/popups/{popupId}/like")
    public ApiResponse<Void> unlike(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long popupId) {
        popupLikeService.unlike(authUser.getUserId(), popupId);
        return ApiResponse.noContent();
    }

    @GetMapping("/api/admin/popups")
    public ApiResponse<List<PopupSummaryResponse>> getAllPopups() {
        return ApiResponse.success(popupService.getAllPopupsForAdmin());
    }

    @PostMapping("/api/admin/popups")
    public ApiResponse<PopupResponse> createPopup(@Valid @RequestBody PopupCreateRequest request) {
        return ApiResponse.success(popupService.createPopup(request));
    }

    @PatchMapping("/api/admin/popups/{popupId}")
    public ApiResponse<PopupResponse> updatePopup(@PathVariable Long popupId,
                                                    @Valid @RequestBody PopupUpdateRequest request) {
        return ApiResponse.success(popupService.updatePopup(popupId, request));
    }

    @DeleteMapping("/api/admin/popups/{popupId}")
    public ApiResponse<Void> deletePopup(@PathVariable Long popupId) {
        popupService.deletePopup(popupId);
        return ApiResponse.noContent();
    }
}
