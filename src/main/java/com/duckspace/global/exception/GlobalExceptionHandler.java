package com.duckspace.global.exception;

import com.duckspace.global.response.ApiResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

        if (status.is5xxServerError()) {
            // 5xx 는 우리 잘못이라 원인을 남겨야 고칠 수 있습니다. 여기서 스택을 안 남기면
            // 서버에 남는 증거가 에러코드 한 줄뿐이라 무엇이 실패했는지 알 수 없습니다.
            // (원인이 실리려면 던지는 쪽이 BusinessException(code, cause) 를 써야 합니다)
            log.error("[{}] {}", errorCode.name(), e.getMessage(), e);
        } else if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
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

        // 한 필드가 @NotBlank 와 @Size 를 동시에 어기면 메시지가 둘입니다. put 이면 나중 것만
        // 남고 어느 쪽이 남을지는 실행마다 다릅니다. 둘 다 보여줘야 사용자가 한 번에 고칩니다.
        // (담는 위치는 data 그대로입니다 — 프론트 연동 가이드에 그렇게 약속돼 있습니다)
        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.merge(
                        error.getField(), error.getDefaultMessage(),
                        (existing, added) -> existing + ", " + added));

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

    /**
     * 없는 경로입니다. 400 이 아니라 <b>404</b> 여야 합니다 — 400 은 "요청 본문이 잘못됐다" 는
     * 뜻이라, 경로 오타에 그걸 돌려주면 호출자가 엉뚱한 데를 봅니다.
     *
     * <p>정적 파일이 없을 때도 여기로 옵니다(로컬 저장소의 지워진 이미지 등).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        log.info("[NO_RESOURCE_FOUND] {}", e.getMessage());
        return errorResponse(HttpStatus.NOT_FOUND, "NO_RESOURCE_FOUND",
                "존재하지 않는 API 입니다. URL 을 다시 확인해주세요.");
    }

    /**
     * 경로는 있는데 메서드가 다릅니다. <b>405</b> 이고, RFC 상 {@code Allow} 헤더로
     * 허용 메서드를 알려줘야 합니다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.info("[METHOD_NOT_ALLOWED] {}", e.getMessage());

        HttpHeaders headers = new HttpHeaders();
        Set<HttpMethod> supported = e.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            headers.setAllow(supported);
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(headers)
                .body(new ApiResponse<>(false, null,
                        new ApiResponse.ErrorDetail("METHOD_NOT_ALLOWED",
                                "이 URL 에서 지원하지 않는 HTTP 메서드입니다."),
                        MDC.get("traceId")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleWrongMediaType(Exception e) {
        log.info("[NOT_SUPPORTED_MEDIA] {}", e.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "NOT_SUPPORTED_MEDIA", "허용하지 않는 미디어타입입니다.");
    }

    /**
     * 업로드 용량 초과. {@link MultipartException} 을 상속해서 아래 일반 핸들러에 흡수되면
     * "잘못된 multipart 요청" 이라는 엉뚱한 안내가 나갑니다 — 사용자가 가장 흔하게 밟는
     * 실패인데(폰 사진이 보통 3~8MB) 이유를 알 수 없게 됩니다.
     *
     * <p>한도는 {@code application.yml} 의 {@code spring.servlet.multipart.max-file-size}
     * 입니다. 그 값을 바꾸면 이 문구도 같이 바꿔주세요.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLargeUpload(MaxUploadSizeExceededException e) {
        log.info("[IMAGE_TOO_LARGE] {}", e.getMessage());
        return errorResponse(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE",
                "파일이 너무 큽니다. 한 장에 10MB 까지 올릴 수 있습니다.");
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