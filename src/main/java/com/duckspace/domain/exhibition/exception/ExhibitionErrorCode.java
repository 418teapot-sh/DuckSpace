package com.duckspace.domain.exhibition.exception;

import com.duckspace.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ExhibitionErrorCode implements BaseErrorCode {

    EXHIBITION_NOT_FOUND(HttpStatus.NOT_FOUND, "장식장을 찾을 수 없습니다."),
    NOT_EXHIBITION_OWNER(HttpStatus.FORBIDDEN, "본인의 장식장만 수정할 수 있습니다."),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "전시된 굿즈를 찾을 수 없습니다."),
    EMPTY_IMAGE(HttpStatus.BAD_REQUEST, "이미지 파일이 비어 있습니다."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "JPG 또는 PNG 이미지만 올릴 수 있습니다."),
    IMAGE_PROCESSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 처리에 실패했습니다."),
    ITEM_NOT_RETRYABLE(HttpStatus.CONFLICT, "처리에 실패한 굿즈만 다시 시도할 수 있습니다."),
    RETRY_SOURCE_MISSING(HttpStatus.CONFLICT, "다시 처리할 원본이 없습니다. 사진을 다시 올려주세요."),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "보관함에서 사진을 찾을 수 없습니다."),
    IMAGE_NOT_RETRYABLE(HttpStatus.CONFLICT, "처리에 실패한 사진만 다시 시도할 수 있습니다."),
    IMAGE_IN_USE(HttpStatus.CONFLICT, "장식장에 배치된 사진은 삭제할 수 없습니다. 먼저 굿즈를 삭제해주세요."),
    ;

    private final HttpStatus status;
    private final String message;

    ExhibitionErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
