package com.duckspace.domain.popup.dto.response;

import com.duckspace.domain.popup.entity.Popup;
import com.duckspace.domain.popup.entity.PopupStatus;

import java.time.LocalDate;

/** 팝업 목록 카드용 — 상세 설명/장소는 빼고 필요한 것만 */
public record PopupSummaryResponse(
        Long id,
        String title,
        String imageUrl,
        LocalDate startDate,
        LocalDate endDate,
        PopupStatus status,
        boolean liked
) {
    public static PopupSummaryResponse from(Popup popup, boolean liked) {
        return new PopupSummaryResponse(
                popup.getId(),
                popup.getTitle(),
                popup.getImageUrl(),
                popup.getStartDate(),
                popup.getEndDate(),
                popup.getStatus(),
                liked
        );
    }

    /** viewer 개념이 없는 경로(관리자 목록 등)용 — "안 좋아함"이 아니라 "이 응답엔 찜 여부가 의미 없음"입니다. */
    public static PopupSummaryResponse from(Popup popup) {
        return from(popup, false);
    }
}
