package com.duckspace.domain.exhibition.exception;

import com.duckspace.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ExhibitionErrorCode implements BaseErrorCode {

    EXHIBITION_NOT_FOUND(HttpStatus.NOT_FOUND, "장식장을 찾을 수 없습니다."),
    NOT_EXHIBITION_OWNER(HttpStatus.FORBIDDEN, "본인의 장식장만 수정할 수 있습니다."),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "전시된 굿즈를 찾을 수 없습니다."),
    SLOT_ALREADY_OCCUPIED(HttpStatus.CONFLICT, "이미 굿즈가 놓인 자리입니다."),
    ;

    private final HttpStatus status;
    private final String message;

    ExhibitionErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
