package com.duckspace.domain.popup.controller;

import com.duckspace.domain.popup.dto.request.PopupCreateRequest;
import com.duckspace.domain.popup.dto.request.PopupUpdateRequest;
import com.duckspace.domain.popup.dto.response.PopupResponse;
import com.duckspace.domain.popup.dto.response.PopupSummaryResponse;
import com.duckspace.domain.popup.service.PopupService;
import com.duckspace.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/api/popups")
    public ApiResponse<List<PopupSummaryResponse>> getUpcomingPopups() {
        return ApiResponse.success(popupService.getUpcomingPopups());
    }

    @GetMapping("/api/popups/{popupId}")
    public ApiResponse<PopupResponse> getPopup(@PathVariable Long popupId) {
        return ApiResponse.success(popupService.getPopup(popupId));
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