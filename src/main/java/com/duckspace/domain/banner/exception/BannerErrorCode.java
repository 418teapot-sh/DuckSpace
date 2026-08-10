package com.duckspace.domain.banner.exception;

import com.duckspace.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum BannerErrorCode implements BaseErrorCode {
    BANNER_NOT_FOUND(HttpStatus.NOT_FOUND, "배너를 찾을 수 없습니다."),
    INVALID_BANNER_PERIOD(HttpStatus.BAD_REQUEST, "노출 종료일은 시작일보다 빠를 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    BannerErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}