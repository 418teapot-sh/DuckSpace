package com.duckspace.domain.popup.dto.response;

import com.duckspace.domain.popup.entity.Popup;
import com.duckspace.domain.popup.entity.PopupStatus;

import java.time.LocalDate;

public record PopupResponse(
        Long id,
        String title,
        String imageUrl,
        String description,
        String location,
        LocalDate startDate,
        LocalDate endDate,
        PopupStatus status,
        String aiSummary,
        boolean liked
) {
    public static PopupResponse from(Popup popup, boolean liked) {
        return new PopupResponse(
                popup.getId(), popup.getTitle(), popup.getImageUrl(), popup.getDescription(),
                popup.getLocation(), popup.getStartDate(), popup.getEndDate(),
                popup.getStatus(), popup.getAiSummary(), liked);
    }

    /** viewer 개념이 없는 경로(관리자 등록/수정 등)용 — "안 좋아함"이 아니라 "이 응답엔 찜 여부가 의미 없음"입니다. */
    public static PopupResponse from(Popup popup) {
        return from(popup, false);
    }
}
