package com.duckspace.domain.post.exception;

import com.duckspace.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum PostErrorCode implements BaseErrorCode {

    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    NOT_POST_OWNER(HttpStatus.FORBIDDEN, "본인 게시글만 가능합니다."),
    INVALID_BOARD_TYPE(HttpStatus.BAD_REQUEST, "이 게시판 타입에서는 지원하지 않는 요청입니다."),
    EXCHANGE_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 교환 완료된 글입니다."),
    CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "본문은 필수입니다."),

    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    NOT_COMMENT_OWNER(HttpStatus.FORBIDDEN, "본인 댓글만 가능합니다."),
    PARENT_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "답글을 달 댓글을 찾을 수 없습니다."),
    REPLY_TO_REPLY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "답글에는 답글을 달 수 없습니다."),
    PARENT_COMMENT_ON_DIFFERENT_POST(HttpStatus.BAD_REQUEST, "다른 게시글의 댓글에는 답글을 달 수 없습니다."),

    ALREADY_LIKED(HttpStatus.CONFLICT, "이미 좋아요한 게시글입니다."),

    CANNOT_REPORT_OWN_CONTENT(HttpStatus.BAD_REQUEST, "본인 게시글/댓글은 신고할 수 없습니다."),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 게시글/댓글입니다."),

    EXCHANGE_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "교환 신청을 찾을 수 없습니다."),
    NOT_APPLICATION_OWNER(HttpStatus.FORBIDDEN, "본인 신청만 가능합니다."),
    SELF_APPLICATION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인 글에는 신청할 수 없습니다."),
    EXCHANGE_APPLICATION_INVALID_STATUS(HttpStatus.CONFLICT, "처리할 수 없는 신청 상태입니다."),
    INVALID_APPLICATION_FILTER(HttpStatus.BAD_REQUEST, "filter는 sent 또는 received만 가능합니다."),
    ;

    private final HttpStatus status;
    private final String message;

    PostErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
