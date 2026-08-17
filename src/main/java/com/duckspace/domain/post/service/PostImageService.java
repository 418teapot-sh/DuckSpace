package com.duckspace.domain.post.service;

import com.duckspace.domain.exhibition.image.ImageInspector;
import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 게시글(잡담/교환) 첨부 이미지 업로드. 전시 도메인의 배경 제거 파이프라인과 달리 후처리가 없어
 * 업로드 즉시 URL을 돌려줍니다 — 글 작성 폼에서 이미지를 먼저 이걸로 올려 URL을 받은 뒤,
 * 그 URL을 CasualPostRequest.imageUrls / OfferedItemRequest.imageUrl 등에 담아 글 작성 요청에
 * 실어 보내는 2단계 방식입니다.
 */
@Service
@RequiredArgsConstructor
public class PostImageService {

    private static final Set<String> SUPPORTED_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

    private final ImageStorage imageStorage;

    public String upload(Long userId, MultipartFile image) {
        byte[] data = readBytes(image);
        validateImage(image, data);

        String format = ImageInspector.detectFormat(data)
                .orElseThrow(() -> new BusinessException(PostErrorCode.UNSUPPORTED_IMAGE_TYPE));
        String key = "posts/%d/%s.%s".formatted(userId, UUID.randomUUID(), format);

        return imageStorage.upload(key, data, image.getContentType());
    }

    /** ExhibitionItemService.validateImage와 같은 이유로 Content-Type 헤더뿐 아니라 실제 바이트도 확인합니다. */
    private void validateImage(MultipartFile image, byte[] data) {
        if (image == null || image.isEmpty() || data.length == 0) {
            throw new BusinessException(PostErrorCode.EMPTY_IMAGE);
        }
        String contentType = image.getContentType();
        if (contentType == null || !SUPPORTED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(PostErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        if (!ImageInspector.isSupported(data)) {
            throw new BusinessException(PostErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new BusinessException(PostErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }
}
