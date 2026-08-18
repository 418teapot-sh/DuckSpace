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
}
