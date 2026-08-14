package com.duckspace.domain.exhibition.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;

/**
 * 굿즈가 지워질 때 실제 이미지 파일도 함께 정리합니다.
 *
 * <p>이게 없으면 DB 행만 사라지고 S3 객체(또는 로컬 파일)는 영원히 남습니다.
 * 지우고 다시 올리기를 반복하는 화면이라 금방 쌓입니다.
 *
 * <p><b>커밋 이후에 지우는 이유:</b> 트랜잭션 안에서 지우면 뒤에 롤백이 났을 때
 * DB 행은 살아있는데 이미지만 사라져, 화면에 깨진 굿즈가 남습니다. 되돌릴 수 없는 작업이라
 * 되돌릴 일이 없어진 다음에 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageCleanup {

    private final ImageStorage imageStorage;

    /** 한 장짜리 편의 메서드. {@code null} 이면 아무것도 하지 않습니다. */
    public void deleteAfterCommit(String imageUrl) {
        deleteAfterCommit(Collections.singletonList(imageUrl));
    }

    /**
     * 트랜잭션이 커밋되면 이미지를 지웁니다. 트랜잭션 밖에서 부르면 즉시 지웁니다.
     *
     * <p>삭제 실패는 로그만 남깁니다. 파일이 남는 것보다 "삭제했습니다" 응답이 실패하는 쪽이
     * 사용자에게 더 나쁩니다.
     */
    public void deleteAfterCommit(List<String> imageUrls) {
        List<String> targets = imageUrls.stream().filter(url -> url != null && !url.isBlank()).toList();
        if (targets.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete(targets);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delete(targets);
            }
        });
    }

    private void delete(List<String> imageUrls) {
        for (String url : imageUrls) {
            try {
                imageStorage.deleteByUrl(url);
            } catch (Exception e) {
                log.warn("이미지 정리 실패: {} ({})", url, e.toString());
            }
        }
    }
}
