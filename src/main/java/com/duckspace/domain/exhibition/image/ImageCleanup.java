package com.duckspace.domain.exhibition.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 굿즈가 지워질 때 실제 이미지 파일도 함께 정리합니다.
 *
 * <p>이게 없으면 DB 행만 사라지고 S3 객체(또는 로컬 파일)는 영원히 남습니다.
 * 지우고 다시 올리기를 반복하는 화면이라 금방 쌓입니다.
 *
 * <p><b>커밋 이후에 지우는 이유:</b> 트랜잭션 안에서 지우면 뒤에 롤백이 났을 때
 * DB 행은 살아있는데 이미지만 사라져, 화면에 깨진 굿즈가 남습니다. 되돌릴 수 없는 작업이라
 * 되돌릴 일이 없어진 다음에 합니다.
 *
 * <p><b>백그라운드로 넘기는 이유:</b> 장식장을 지우면 안에 있던 굿즈 수만큼 S3 왕복이 생깁니다.
 * 그걸 요청 스레드에서 하나씩 하면 굿즈가 많을수록 삭제 응답이 느려집니다. 사용자 입장에서
 * 삭제는 이미 끝난 일이라 기다릴 이유가 없습니다.
 *
 * <p><b>이미지 처리와 다른 실행기를 쓰는 이유:</b> 같이 쓰면 굿즈를 많이 지운 사용자 하나가
 * 스레드를 오래 붙잡아, 그 사이 다른 사용자가 올린 사진 처리가 밀립니다.
 * ({@code ExhibitionAsyncConfig.CLEANUP_EXECUTOR})
 */
@Slf4j
@Component
public class ImageCleanup {

    private final ImageStorage imageStorage;
    private final Executor cleanupExecutor;

    public ImageCleanup(ImageStorage imageStorage,
                        @Qualifier("exhibitionCleanupExecutor") Executor cleanupExecutor) {
        this.imageStorage = imageStorage;
        this.cleanupExecutor = cleanupExecutor;
    }

    /** 한 장짜리 편의 메서드. {@code null} 이면 아무것도 하지 않습니다. */
    public void deleteAfterCommit(String imageUrl) {
        deleteAfterCommit(Collections.singletonList(imageUrl));
    }

    /**
     * 트랜잭션이 커밋되면 이미지를 지웁니다. 트랜잭션 밖에서 부르면 바로 예약합니다.
     */
    public void deleteAfterCommit(List<String> imageUrls) {
        List<String> targets = clean(imageUrls);
        if (targets.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submit(targets);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(targets);
            }
        });
    }

    /**
     * 지금 바로 지웁니다. 트랜잭션과 무관한 곳(백그라운드 이미지 처리)에서 씁니다.
     *
     * <p>실패는 로그만 남깁니다. 이미 다른 일이 성공한 뒤라, 정리에 실패했다고 그 일을
     * 되돌릴 수는 없습니다.
     */
    public void delete(String imageUrl) {
        for (String url : clean(Collections.singletonList(imageUrl))) {
            deleteQuietly(url);
        }
    }

    private void submit(List<String> imageUrls) {
        try {
            cleanupExecutor.execute(() -> imageUrls.forEach(this::deleteQuietly));
        } catch (RejectedExecutionException e) {
            // 큐가 가득 찼습니다. 정리를 통째로 건너뛰면 객체가 영영 남으므로,
            // 느려지더라도 이 자리에서 지웁니다.
            log.warn("이미지 정리를 큐에 넣지 못해 즉시 처리합니다. {}건", imageUrls.size());
            imageUrls.forEach(this::deleteQuietly);
        }
    }

    private void deleteQuietly(String imageUrl) {
        try {
            imageStorage.deleteByUrl(imageUrl);
        } catch (Exception e) {
            log.warn("이미지 정리 실패: {} ({})", imageUrl, e.toString());
        }
    }

    private static List<String> clean(List<String> imageUrls) {
        return imageUrls.stream().filter(url -> url != null && !url.isBlank()).toList();
    }
}
