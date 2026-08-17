package com.duckspace.domain.post.service;

import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.post.entity.PendingPostImage;
import com.duckspace.domain.post.repository.PendingPostImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
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

        // imageStorage.deleteByUrl은 실패해도 내부에서 로그만 남기고 삼키므로 여기서 따로 감쌀 필요가 없습니다.
        abandoned.forEach(image -> imageStorage.deleteByUrl(image.getImageUrl()));
        pendingPostImageRepository.deleteAll(abandoned);

        log.info("글 작성에 안 쓰인 업로드 이미지 {}건 정리", abandoned.size());
    }
}
