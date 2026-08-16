package com.duckspace.domain.popup.exception;

import com.duckspace.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum PopupErrorCode implements BaseErrorCode {
    POPUP_NOT_FOUND(HttpStatus.NOT_FOUND, "팝업을 찾을 수 없습니다."),
    INVALID_POPUP_PERIOD(HttpStatus.BAD_REQUEST, "종료일은 시작일보다 빠를 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    PopupErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}