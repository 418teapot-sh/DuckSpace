package com.duckspace.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 도메인 에러코드 enum이 구현하는 인터페이스.
 *
 * <p>mutsa 프로젝트의 BaseErrorCode 패턴 — 팀원마다 자기 도메인 패키지에
 * 이 인터페이스를 구현하는 enum을 각자 만들면, Billage/pick-eat처럼 ErrorCode
 * 파일 하나를 셋이 같이 건드리면서 생기는 merge 충돌이 없어집니다.
 *
 * <p>예시:
 * <pre>{@code
 * public enum RestaurantErrorCode implements BaseErrorCode {
 *     RESTAURANT_NOT_FOUND(HttpStatus.NOT_FOUND, "가게를 찾을 수 없습니다.");
 *
 *     private final HttpStatus status;
 *     private final String message;
 *     ...
 * }
 * }</pre>
 */
public interface BaseErrorCode {

    HttpStatus getStatus();

    String getMessage();

    /** enum이 구현하면 자동으로 제공됩니다. (Enum.name()) */
    String name();
}
