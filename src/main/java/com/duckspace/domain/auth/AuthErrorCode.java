package com.duckspace.domain.auth;

import com.duckspace.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum AuthErrorCode implements BaseErrorCode {

    INVALID_CODE(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 인가 코드입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    OAUTH_ONLY_ACCOUNT(HttpStatus.CONFLICT, "구글 로그인으로 가입된 계정입니다. 구글 로그인을 이용해주세요."),
    ;

    private final HttpStatus status;
    private final String message;

    AuthErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
