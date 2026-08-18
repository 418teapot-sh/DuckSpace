package com.duckspace.domain.user.service;

import com.duckspace.domain.exhibition.image.ImageInspector;
import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.user.exception.UserErrorCode;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 프로필 사진 업로드.
 *
 * <p>글 첨부 이미지({@link com.duckspace.domain.post.service.PostImageService})와 달리
 * "먼저 올려 URL 받고 → 나중에 글에 담기"는 2단계가 필요 없습니다. 유저당 프로필 사진은
 * 한 장뿐이라 업로드와 동시에 바로 {@link ProfileImageWriter}로 반영합니다.
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
    private final UserRepository userRepository;
    private final ProfileImageWriter profileImageWriter;

    /**
     * S3/디스크 왕복(외부 I/O)은 일부러 트랜잭션 밖에서 합니다. {@code ExhibitionImageProcessor}가
     * 같은 이유로 트랜잭션을 안 거는 것과 같습니다 — 느려지는 순간 그만큼 DB 커넥션을 붙잡게 됩니다.
     * DB 조회·갱신만 {@link ProfileImageWriter#apply}에서 짧은 트랜잭션으로 묶습니다.
     */
    public String upload(Long userId, MultipartFile image) {
        validateNotEmpty(image);
        byte[] data = readBytes(image);
        validateFormat(image, data);

        // 존재하지 않는 유저에 업로드했다가 뒤늦게 실패하면 S3에 고아 파일만 남으므로,
        // 무거운 업로드 전에 먼저 확인합니다.
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        String format = ImageInspector.detectFormat(data)
                .orElseThrow(() -> new BusinessException(UserErrorCode.UNSUPPORTED_IMAGE_TYPE));
        String key = "users/%d/%s.%s".formatted(userId, UUID.randomUUID(), format);
        // 클라이언트가 보낸 Content-Type 헤더가 아니라 바이트로 판별한 실제 포맷을 씁니다.
        // 헤더는 자칭일 뿐이라 어긋나면(예: PNG인데 image/jpg로 보냄) S3에 잘못된
        // Content-Type으로 저장되어 브라우저가 잘못 렌더링할 수 있습니다.
        String imageUrl = imageStorage.upload(key, data, "image/" + format);

        profileImageWriter.apply(userId, imageUrl);
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
            throw new BusinessException(UserErrorCode.IMAGE_UPLOAD_FAILED);
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
