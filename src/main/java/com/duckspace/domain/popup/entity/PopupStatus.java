package com.duckspace.domain.popup.entity;

import java.time.LocalDate;
import java.time.ZoneId;

public enum PopupStatus {
    UPCOMING,
    ONGOING,
    ENDED;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static PopupStatus of(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(KST);
        if (today.isBefore(startDate)) {
            return UPCOMING;
        }
        if (today.isAfter(endDate)) {
            return ENDED;
        }
        return ONGOING;
    }
}
