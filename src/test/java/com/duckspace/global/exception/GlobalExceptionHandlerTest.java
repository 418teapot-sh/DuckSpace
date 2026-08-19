package com.duckspace.global.exception;

import com.duckspace.global.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 에러 응답 계약을 고정합니다.
 *
 * <p>핸들러는 스프링 컨텍스트 없이도 부를 수 있는 평범한 메서드라, 여기서는 컨텍스트를 띄우지
 * 않고 직접 호출합니다. 상태코드와 {@code error.code} 는 프론트가 분기에 쓰는 값이라
 * 바뀌면 화면이 깨집니다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("없는 경로는 400 이 아니라 404 다")
    void 없는_경로는_404() {
        // 400 은 "요청 본문이 잘못됐다" 는 뜻이라, 경로 오타에 그걸 주면 호출자가 엉뚱한 데를 봅니다.
        // 생성자 시그니처가 스프링 버전마다 달라서 목으로 둡니다 — 핸들러가 쓰는 건 메시지뿐입니다.
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNoResource(mock(NoResourceFoundException.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error().code()).isEqualTo("NO_RESOURCE_FOUND");
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405 이고 Allow 헤더로 허용 메서드를 알려준다")
    void 메서드가_다르면_405() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("POST", List.of("GET", "DELETE")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().error().code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getHeaders().getAllow())
                .as("RFC 상 405 는 Allow 헤더로 허용 메서드를 알려줘야 합니다")
                .containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Test
    @DisplayName("용량 초과 업로드는 413 이고 이유를 알려준다")
    void 용량_초과는_413() {
        // MultipartException 을 상속해서, 전용 핸들러가 없으면 "잘못된 multipart 요청" 으로
        // 흡수됩니다. 폰 사진(3~8MB)이 가장 흔하게 밟는 실패라 이유가 보여야 합니다.
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleTooLargeUpload(new MaxUploadSizeExceededException(10L * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().error().code()).isEqualTo("IMAGE_TOO_LARGE");
        assertThat(response.getBody().error().message()).contains("10MB");
    }

    @Test
    @DisplayName("용량 초과가 아닌 multipart 오류는 그대로 400 이다")
    void 일반_multipart_오류는_400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleInvalidMultiPartFormRequest(new MultipartException("boundary 없음"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().code()).isEqualTo("INVALID_MULTIPART");
    }

    @Test
    @DisplayName("한 필드가 규칙 두 개를 어기면 메시지가 둘 다 나온다")
    void 필드_에러가_겹쳐도_사라지지_않는다() {
        // put 이면 나중 것만 남고, 어느 쪽이 남을지는 실행마다 다릅니다.
        // 사용자가 한 번에 고칠 수 있으려면 둘 다 보여야 합니다.
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "nickname", "비어 있을 수 없습니다"));
        bindingResult.addError(new FieldError("request", "nickname", "30자 이하여야 합니다"));

        MethodArgumentNotValidException e = mock(MethodArgumentNotValidException.class);
        given(e.getBindingResult()).willReturn(bindingResult);

        ResponseEntity<ApiResponse<Map<String, String>>> response = handler.handleValidationExceptions(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().data().get("nickname"))
                .contains("비어 있을 수 없습니다")
                .contains("30자 이하여야 합니다");
        assertThat(response.getBody().error().code())
                .as("필드 메시지는 data 에, 코드는 error 에 — 연동 가이드에 약속된 형태입니다")
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("BusinessException 이 원인 예외를 잃지 않는다")
    void 원인을_잃지_않는다() {
        // 원인을 버리면 5xx 가 났을 때 서버에 남는 증거가 에러코드 한 줄뿐이라,
        // 디스크가 찬 건지 권한 문제인지 알 방법이 없습니다.
        Exception cause = new IllegalStateException("disk full");

        BusinessException exception = new BusinessException(GlobalErrorCode.VALIDATION_FAILED, cause);

        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.VALIDATION_FAILED);
    }
}
