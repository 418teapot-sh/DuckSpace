package com.duckspace.domain.exhibition.image;

import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.global.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * 업로드된 파일이 정말 이미지인지 확인합니다. 굿즈 업로드와 보관함 업로드가 함께 씁니다.
 *
 * <p><b>{@code Content-Type} 헤더만 믿으면 안 됩니다.</b> 클라이언트가 보내는 값이라
 * 아무 파일에나 {@code image/png} 를 붙일 수 있고, 그러면 처리에 실패한 뒤 원본이 그대로
 * 저장되어 <b>공개 URL 로 서빙됩니다</b> — 업로드 창구가 곧 파일 호스팅이 됩니다.
 * 그래서 실제 바이트를 디코더에 물어봅니다. ({@link ImageInspector})
 */
public final class MultipartImageValidator {

    /** 브라우저가 보내는 이미지 MIME 타입. ImageIO 가 읽을 수 있는 형식으로 제한합니다. */
    private static final Set<String> SUPPORTED_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

    private MultipartImageValidator() {
    }

    public static byte[] readBytes(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ExhibitionErrorCode.EMPTY_IMAGE);
        }
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ExhibitionErrorCode.IMAGE_PROCESSING_FAILED, e);
        }
    }

    /** 헤더와 실제 바이트를 모두 확인합니다. 행을 만들기 전에 불러야 PENDING 껍데기가 안 남습니다. */
    public static void validate(MultipartFile image, byte[] data) {
        if (data.length == 0) {
            throw new BusinessException(ExhibitionErrorCode.EMPTY_IMAGE);
        }
        String contentType = image.getContentType();
        if (contentType == null || !SUPPORTED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ExhibitionErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        if (!ImageInspector.isSupported(data)) {
            throw new BusinessException(ExhibitionErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        // 용량 제한(10MB)만으로는 디코딩 후 크기를 못 막습니다 — 고압축 PNG 는 몇 MB 로도
        // 수천만 픽셀이 됩니다. 이 확인이 없으면 200 으로 접수됐다가 백그라운드에서
        // 이유 없이 FAILED 로 끝나서, 사용자는 왜 실패했는지 알 수 없습니다.
        if (!ImageInspector.withinPixelLimit(data)) {
            throw new BusinessException(ExhibitionErrorCode.IMAGE_DIMENSION_TOO_LARGE);
        }
    }
}
