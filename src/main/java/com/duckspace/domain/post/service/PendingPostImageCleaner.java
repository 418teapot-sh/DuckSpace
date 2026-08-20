package com.duckspace.domain.post.service;

import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.post.entity.PendingPostImage;
import com.duckspace.domain.post.repository.PendingPostImageRepository;
import com.duckspace.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * {@code POST /api/posts/images}로 올렸지만 글 작성까지 이어지지 않은 이미지를 주기적으로 지웁니다.
 *
 * <p>사용자가 사진만 올리고 글쓰기를 중간에 그만두면 저장소(S3/로컬)엔 파일이 남는데,
 * 이 시점엔 어느 Post도 참조하지 않아 exhibition의 {@code ImageCleanup}처럼 "행이 지워질 때
 * 같이 지운다"를 걸 곳이 없습니다. 그래서 대신 일정 시간 지나도 여전히 안 쓰인 채면
 * (=업로드 이후 아무 글 작성 요청도 이 URL을 claim하지 않았으면) 정리 대상으로 봅니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingPostImageCleaner {

    /** 이보다 오래 안 쓰인 채 남아있으면 정리 대상입니다. 업로드 후 바로 글을 쓰는 정상 흐름보다 넉넉히 잡았습니다. */
    private static final Duration ABANDONED_THRESHOLD = Duration.ofHours(24);

    /** 한 번에 지우는 최대 건수. 정리 대상이 몰려도 이 작업이 오래 스레드를 붙잡지 않도록 제한합니다. */
    private static final int BATCH_SIZE = 200;

    private final PendingPostImageRepository pendingPostImageRepository;
    private final UserRepository userRepository;
    private final ImageStorage imageStorage;

    @Scheduled(fixedRate = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void cleanupAbandoned() {
        LocalDateTime cutoff = LocalDateTime.now().minus(ABANDONED_THRESHOLD);
        List<PendingPostImage> abandoned =
                pendingPostImageRepository.findByCreatedAtBefore(cutoff, PageRequest.of(0, BATCH_SIZE));
        if (abandoned.isEmpty()) {
            return;
        }

        Set<String> claimed = claimedAsProfileImage(abandoned);

        List<PendingPostImage> deletable = abandoned.stream()
                .filter(image -> !claimed.contains(image.getImageUrl()))
                .toList();

        // imageStorage.deleteByUrl은 실패해도 내부에서 로그만 남기고 삼키므로 여기서 따로 감쌀 필요가 없습니다.
        deletable.forEach(image -> imageStorage.deleteByUrl(image.getImageUrl()));

        // 프로필로 쓰이는 것도 표시(행)는 지웁니다. 마커의 뜻이 "아직 아무 데도 안 쓰임" 인데
        // 이미 쓰이고 있으니 남길 이유가 없고, 남기면 매시간 같은 행을 다시 집어옵니다.
        // BATCH_SIZE 가 200 이라 그런 행이 배치를 채우면 진짜 고아 이미지가 영영 안 지워집니다.
        pendingPostImageRepository.deleteAll(abandoned);

        if (!claimed.isEmpty()) {
            log.info("프로필 사진으로 쓰이고 있어 {}건은 파일을 남겨둡니다.", claimed.size());
        }
        log.info("글 작성에 안 쓰인 업로드 이미지 {}건 정리", deletable.size());
    }

    /**
     * 지우려는 것 중 <b>프로필 사진으로 쓰이고 있는</b> URL.
     *
     * <p>프론트가 프로필 사진을 전용 엔드포인트가 아니라 게시글 이미지 업로드로 올리고 있어서,
     * 그대로 두면 "글에 안 쓰인 이미지" 로 잡혀 <b>24시간 뒤 파일이 사라집니다.</b>
     * {@code users.profile_image_url} 은 남고 파일만 없어져 깨진 아바타가 됩니다.
     *
     * <p>업로드 경로를 고치는 대신 <b>지우기 직전에</b> 확인하는 이유는, 어떤 경로로 올라오든
     * 같은 사고가 안 나게 하기 위해서입니다. {@code ImageCleanup#findReferencedUrls} 도
     * 같은 방식으로 삭제 직전에 참조 여부를 봅니다.
     */
    private Set<String> claimedAsProfileImage(List<PendingPostImage> abandoned) {
        List<String> urls = abandoned.stream()
                .map(PendingPostImage::getImageUrl)
                .filter(Objects::nonNull)
                .toList();
        if (urls.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(userRepository.findProfileImageUrlsIn(urls));
    }
}
