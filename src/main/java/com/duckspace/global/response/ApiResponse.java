package com.duckspace.global.response;

import com.duckspace.global.exception.BaseErrorCode;
import org.slf4j.MDC;

public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorDetail error,
        String traceId
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, currentTraceId());
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(true, null, null, currentTraceId());
    }

    public static ApiResponse<Void> error(BaseErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null, new ErrorDetail(errorCode.name(), message), currentTraceId());
    }

    /** TraceIdFilter가 MDC에 넣어둔 값을 그대로 응답에 실어줍니다. */
    private static String currentTraceId() {
        return MDC.get("traceId");
    }

    public record ErrorDetail(String code, String message) {
    }
}