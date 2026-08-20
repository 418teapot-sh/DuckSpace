package com.duckspace.domain.post.service;

import com.duckspace.domain.exhibition.image.ImageCleanup;
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
 * <p><b>실제로 지울지는 {@link ImageCleanup} 이 정합니다.</b> 여기서 고르는 것은 "24시간이
 * 지나도록 글에 안 담긴 것" 까지고, 그 URL 이 <b>다른 데서 쓰이고 있는지</b>(보관함 · 다른 굿즈 ·
 * 프로필 사진)는 삭제 직전에 {@code ImageCleanup} 이 확인합니다. 판단을 여기에도 두면 참조
 * 소스가 늘 때마다 두 곳을 고쳐야 합니다.
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
    private final ImageCleanup imageCleanup;

    @Scheduled(fixedRate = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void cleanupAbandoned() {
        LocalDateTime cutoff = LocalDateTime.now().minus(ABANDONED_THRESHOLD);
        List<PendingPostImage> abandoned =
                pendingPostImageRepository.findByCreatedAtBefore(cutoff, PageRequest.of(0, BATCH_SIZE));
        if (abandoned.isEmpty()) {
            return;
        }

        // 표시(행)는 여기서 지웁니다. 뜻이 "아직 아무 데도 안 쓰임" 인데, 쓰이고 있든 아니든
        // 24시간이 지난 시점에 이 표시는 더 볼 일이 없습니다. 남기면 매시간 같은 행을 다시
        // 집어오고, BATCH_SIZE 가 200 이라 그런 행이 배치를 채우면 진짜 고아가 안 지워집니다.
        pendingPostImageRepository.deleteAll(abandoned);

        // 파일 삭제는 ImageCleanup 에 맡깁니다. 여기서 직접 지우면 "지워도 되는가" 판단이
        // 두 곳으로 갈라지고(프로필 사진처럼 뒤늦게 생긴 참조를 한쪽만 알게 됩니다),
        // 트랜잭션 안에서 S3 를 최대 200번 왕복하게 됩니다.
        // ImageCleanup 은 커밋 후 전용 실행기에서 돌며 참조 확인 실패 시 재시도까지 합니다.
        imageCleanup.deleteAfterCommit(abandoned.stream()
                .map(PendingPostImage::getImageUrl)
                .toList());

        log.info("글 작성에 안 쓰인 업로드 이미지 {}건 정리 요청", abandoned.size());
    }
}
