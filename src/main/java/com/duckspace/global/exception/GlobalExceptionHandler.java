package com.duckspace.global.exception;

import com.duckspace.global.response.ApiResponse;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        BaseErrorCode errorCode = e.getErrorCode();
        HttpStatus status = errorCode.getStatus();

        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            log.warn("[{}] {}", errorCode.name(), e.getMessage());
        } else {
            log.info("[{}] {}", errorCode.name(), e.getMessage());
        }

        return ResponseEntity.status(status)
                .body(ApiResponse.error(errorCode, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException e) {
        log.info("[VALIDATION_FAILED] {}", e.getMessage());

        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.status(GlobalErrorCode.VALIDATION_FAILED.getStatus())
                .body(new ApiResponse<>(false, fieldErrors,
                        new ApiResponse.ErrorDetail(GlobalErrorCode.VALIDATION_FAILED.name(),
                                GlobalErrorCode.VALIDATION_FAILED.getMessage()),
                        MDC.get("traceId")));
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            ConversionFailedException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(Exception e) {
        log.info("[BINDING_ERROR] {}", e.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "BINDING_ERROR", "요청 파라미터 형식이 잘못되었습니다.");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestFormat(Exception e) {
        log.info("[PARSING_ERROR] {}", e.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "PARSING_ERROR", "요청 형식이 잘못되었습니다.");
    }

    @ExceptionHandler({
            HttpRequestMethodNotSupportedException.class,
            NoResourceFoundException.class,
    })
    public ResponseEntity<ApiResponse<Void>> handleWrongRequest(Exception e) {
        log.info("[NO_RESOURCE_FOUND] {}", e.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "NO_RESOURCE_FOUND",
                "존재하지 않은 API에 대한 요청입니다. HTTP 메서드와 URL을 다시 확인해주세요.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleWrongMediaType(Exception e) {
        log.info("[NOT_SUPPORTED_MEDIA] {}", e.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "NOT_SUPPORTED_MEDIA", "허용하지 않는 미디어타입입니다.");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidMultiPartFormRequest(Exception e) {
        log.info("[INVALID_MULTIPART] {}", e.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_MULTIPART", "잘못된 multipart/form-data 요청입니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception e) {
        log.error("[INTERNAL_SERVER_ERROR] {}", e.getMessage(), e);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "예상치 못한 오류가 발생했습니다.");
    }

    private ResponseEntity<ApiResponse<Void>> errorResponse(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiResponse<>(false, null, new ApiResponse.ErrorDetail(code, message), MDC.get("traceId")));
    }
}