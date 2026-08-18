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
}
