package com.duckspace.domain.exhibition.exception;

import com.duckspace.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ExhibitionErrorCode implements BaseErrorCode {
    EXHIBITION_NOT_FOUND(HttpStatus.NOT_FOUND, "전시장을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ExhibitionErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}