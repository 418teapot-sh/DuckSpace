package com.duckspace.domain.chat.exception;

import com.duckspace.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ChatErrorCode implements BaseErrorCode {

    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    NOT_ROOM_PARTICIPANT(HttpStatus.FORBIDDEN, "이 채팅방에 접근할 권한이 없습니다."),
    PARTNER_NOT_FOUND(HttpStatus.NOT_FOUND, "상대방을 찾을 수 없습니다."),
    CANNOT_CHAT_WITH_SELF(HttpStatus.BAD_REQUEST, "자기 자신과는 채팅할 수 없습니다."),
    CONFLICTING_CURSORS(HttpStatus.BAD_REQUEST, "after 와 before 는 함께 쓸 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String message;

    ChatErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
