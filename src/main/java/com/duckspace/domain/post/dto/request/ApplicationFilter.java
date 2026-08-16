package com.duckspace.domain.post.dto.request;

import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.global.exception.BusinessException;

import java.util.Locale;

/** GET /api/applications?filter= 값. 유효한 값 목록을 이 enum 하나로 모읍니다. */
public enum ApplicationFilter {
    SENT,
    RECEIVED;

    public static ApplicationFilter from(String value) {
        if (value == null) {
            throw new BusinessException(PostErrorCode.INVALID_APPLICATION_FILTER);
        }
        try {
            return ApplicationFilter.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(PostErrorCode.INVALID_APPLICATION_FILTER);
        }
    }
}
