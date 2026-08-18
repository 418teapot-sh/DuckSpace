package com.duckspace.domain.user.service;

import com.duckspace.domain.exhibition.image.ImageCleanup;
import com.duckspace.domain.exhibition.image.ImageInspector;
import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.exception.UserErrorCode;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 프로필 사진 업로드.
 *
 * <p>글 첨부 이미지({@link com.duckspace.domain.post.service.PostImageService})와 달리
 * "먼저 올려 URL 받고 → 나중에 글에 담기"는 2단계가 필요 없습니다. 유저당 프로필 사진은
 * 한 장뿐이라 업로드와 동시에 바로 {@link User#replaceProfileImage}로 반영합니다.
 *
 * <p>그래서 게시글용 {@code /api/images}를 프로필 사진에 재사용하면 안 됩니다 — 그 API는
 * 업로드분을 {@code PendingPostImage}로 표시해뒀다가 24시간 안에 글에 안 쓰이면 자동
 * 삭제하는데(방치 정리), 프로필 사진은 애초에 "글에 쓰인다"는 개념이 없어 영원히 방치로
 * 잡혀 삭제됩니다.
 */
@Service
@RequiredArgsConstructor
public class ProfileImageService {

    private static final Set<String> SUPPORTED_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

    private final ImageStorage imageStorage;
    private final ImageCleanup imageCleanup;
    private final UserRepository userRepository;

    @Transactional
    public String upload(Long userId, MultipartFile image) {
        validateNotEmpty(image);
        byte[] data = readBytes(image);
        validateFormat(image, data);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        String format = ImageInspector.detectFormat(data)
                .orElseThrow(() -> new BusinessException(UserErrorCode.UNSUPPORTED_IMAGE_TYPE));
        String key = "users/%d/%s.%s".formatted(userId, UUID.randomUUID(), format);
        String imageUrl = imageStorage.upload(key, data, image.getContentType());

        String previousImageUrl = user.replaceProfileImage(imageUrl);
        imageCleanup.deleteAfterCommit(previousImageUrl);

        return imageUrl;
    }

    private static void validateNotEmpty(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(UserErrorCode.EMPTY_IMAGE);
        }
    }

    private static byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** ExhibitionItemService/PostImageService와 같은 이유로 Content-Type 헤더뿐 아니라 실제 바이트도 확인합니다. */
    private static void validateFormat(MultipartFile image, byte[] data) {
        if (data.length == 0) {
            throw new BusinessException(UserErrorCode.EMPTY_IMAGE);
        }
        String contentType = image.getContentType();
        if (contentType == null || !SUPPORTED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(UserErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        if (!ImageInspector.isSupported(data)) {
            throw new BusinessException(UserErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
    }
}
