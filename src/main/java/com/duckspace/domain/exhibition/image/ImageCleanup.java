package com.duckspace.domain.exhibition.image;

import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import com.duckspace.domain.exhibition.repository.GoodsImageRepository;
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
 * 이미지 파일 삭제의 <b>유일한 출입구</b>입니다. 여기 말고 다른 곳에서 파일을 지우지 마세요.
 *
 * <p>이게 없으면 DB 행만 사라지고 S3 객체(또는 로컬 파일)는 영원히 남습니다.
 * 지우고 다시 올리기를 반복하는 화면이라 금방 쌓입니다.
 *
 * <h2>지우기 직전에 참조를 다시 확인합니다</h2>
 *
 * <p>같은 URL 을 보관함({@code goods_image})과 여러 굿즈({@code exhibition_item})가 공유할 수
 * 있습니다. 호출부가 "지워도 되는지" 를 각자 미리 판단하게 두면 두 가지가 깨집니다 —
 * <b>판단을 빼먹는 경로가 생기고</b>(실제로 재시도 경로에서 빠졌었습니다), 판단과 삭제 사이에
 * <b>새 배치가 끼어드는 경합</b>이 남습니다. 그래서 판단을 이 클래스 한 곳으로 모으고,
 * 시점도 커밋 이후 <b>삭제 실행 직전</b>으로 고정합니다. 그 시점엔 삭제 트랜잭션의 행이
 * 이미 사라진 뒤라 "남아 있는 참조 = 지우면 안 되는 참조" 로 판단이 단순해집니다.
 *
 * <p><b>커밋 이후에 지우는 이유:</b> 트랜잭션 안에서 지우면 뒤에 롤백이 났을 때
 * DB 행은 살아있는데 이미지만 사라져, 화면에 깨진 굿즈가 남습니다.
 *
 * <p><b>백그라운드로 넘기는 이유:</b> 장식장을 지우면 굿즈 수만큼 S3 왕복이 생기는데,
 * 요청 스레드에서 하나씩 하면 삭제 응답이 그만큼 느려집니다.
 */
@Slf4j
@Component
public class ImageCleanup {

    private final ImageStorage imageStorage;
    private final ExhibitionItemRepository exhibitionItemRepository;
    private final GoodsImageRepository goodsImageRepository;
    private final Executor cleanupExecutor;

    public ImageCleanup(ImageStorage imageStorage,
                        ExhibitionItemRepository exhibitionItemRepository,
                        GoodsImageRepository goodsImageRepository,
                        @Qualifier("exhibitionCleanupExecutor") Executor cleanupExecutor) {
        this.imageStorage = imageStorage;
        this.exhibitionItemRepository = exhibitionItemRepository;
        this.goodsImageRepository = goodsImageRepository;
        this.cleanupExecutor = cleanupExecutor;
    }

    /** 한 장짜리 편의 메서드. {@code null} 이면 아무것도 하지 않습니다. */
    public void deleteAfterCommit(String imageUrl) {
        deleteAfterCommit(Collections.singletonList(imageUrl));
    }

    /** 트랜잭션이 커밋되면 이미지를 지웁니다. 트랜잭션 밖에서 부르면 바로 예약합니다. */
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
            // 삭제 직전에 다시 확인합니다. 예약 시점과 지금 사이에 이 URL 이
            // 새로 배치됐을 수 있습니다(체크-후-삭제 경합).
            if (isReferenced(imageUrl)) {
                log.info("아직 참조 중이라 파일을 남깁니다: {}", imageUrl);
                return;
            }
            imageStorage.deleteByUrl(imageUrl);
        } catch (Exception e) {
            log.warn("이미지 정리 실패: {} ({})", imageUrl, e.toString());
        }
    }

    /** 이 URL 을 아직 가리키는 행이 하나라도 있는가. 있으면 지우는 순간 그쪽 그림이 깨집니다. */
    private boolean isReferenced(String imageUrl) {
        return exhibitionItemRepository.existsByImageUrl(imageUrl)
                || goodsImageRepository.existsByImageUrl(imageUrl);
    }

    private static List<String> clean(List<String> imageUrls) {
        return imageUrls.stream().filter(url -> url != null && !url.isBlank()).toList();
    }
}
