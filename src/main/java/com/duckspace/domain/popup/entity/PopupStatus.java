package com.duckspace.domain.popup.entity;

import java.time.LocalDate;

public enum PopupStatus {
    UPCOMING,
    ONGOING,
    ENDED;

    public static PopupStatus of(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        if (today.isBefore(startDate)) {
            return UPCOMING;
        }
        if (today.isAfter(endDate)) {
            return ENDED;
        }
        return ONGOING;
    }
}