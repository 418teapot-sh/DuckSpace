package com.duckspace.domain.popup.entity;

import com.duckspace.global.support.ServiceZone;

import java.time.LocalDate;

public enum PopupStatus {
    UPCOMING,
    ONGOING,
    ENDED;

    public static PopupStatus of(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(ServiceZone.ZONE);
        if (today.isBefore(startDate)) {
            return UPCOMING;
        }
        if (today.isAfter(endDate)) {
            return ENDED;
        }
        return ONGOING;
    }
}
