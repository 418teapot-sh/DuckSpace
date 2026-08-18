package com.duckspace.domain.exhibition.image;

import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import com.duckspace.domain.exhibition.repository.GoodsImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 이미지 파일 삭제의 <b>유일한 출입구</b>입니다. 여기 말고 다른 곳에서 파일을 지우지 마세요.
 *
 * <p>이게 없으면 DB 행만 사라지고 S3 객체(또는 로컬 파일)는 영원히 남습니다.
 *
 * <h2>지우기 직전에 참조를 다시 확인합니다</h2>
 *
 * <p>같은 URL 을 보관함({@code goods_image})과 여러 굿즈({@code exhibition_item})가 공유할 수
 * 있습니다. "지워도 되는가" 판단을 호출부마다 두면 빼먹는 경로가 생기고(실제로 재시도 경로에서
 * 빠졌었습니다), 판단과 삭제 사이에 새 배치가 끼어드는 경합도 남습니다. 그래서 판단을 이 클래스
 * 한 곳으로 모으고, 시점도 <b>삭제 실행 직전(전용 실행기 안)</b>으로 고정합니다.
 *
 * <p><b>수용한 리스크:</b> 참조 확인과 스토리지 삭제 사이의 수 ms 창은 남습니다.
 * {@code POST /items} 가 imageUrl 의 소유를 검증하지 않기 때문에, 같은 URL 에 대한 배치와
 * 삭제가 겹치는 <b>정상 트래픽에서도</b> 이 창이 열려 방금 배치된 그림이 깨질 수 있습니다.
 * 완전히 닫으려면 참조 카운팅(또는 배치 시점 잠금)이 필요한데, 창 크기 대비 비용이 맞지 않아
 * 여기서 멈췄습니다.
 *
 * <p><b>모든 실제 삭제는 전용 실행기(cleanup executor)에서 돕니다.</b> 참조 확인(DB 쿼리)과
 * S3 왕복을 요청 스레드나 이미지 처리 스레드가 떠안지 않게 하기 위해서입니다 — 이미지
 * 실행기는 스레드 2개뿐이라 여기서 지연되면 정상 업로드가 큐에서 거절됩니다.
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
            submit(() -> deleteUnreferenced(targets));
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(() -> deleteUnreferenced(targets));
            }
        });
    }

    /**
     * 참조 확인을 거쳐 지웁니다. 트랜잭션과 무관한 곳(재시도 성공 후 원본 정리)에서 씁니다.
     *
     * <p>확인과 삭제 모두 전용 실행기로 넘어가므로 <b>호출 스레드를 붙잡지 않습니다.</b>
     */
    public void delete(String imageUrl) {
        List<String> targets = clean(Collections.singletonList(imageUrl));
        if (!targets.isEmpty()) {
            submit(() -> deleteUnreferenced(targets));
        }
    }

    /**
     * <b>참조 확인 없이</b> 지웁니다 — 방금 이 흐름이 만들었고 어떤 행도 가리킨 적 없는
     * 고아 객체(상태 기록 실패/no-op 로 버려진 처리본) 전용입니다.
     *
     * <p>그런 URL 은 DB 에 존재할 수가 없어서, 확인 쿼리는 항상 false 를 돌려주는 낭비입니다.
     * <b>행이 가리킨 적 있는 URL 에는 절대 쓰지 마세요</b> — 보호 없이 지워집니다.
     */
    public void deleteOrphan(String imageUrl) {
        List<String> targets = clean(Collections.singletonList(imageUrl));
        if (!targets.isEmpty()) {
            submit(() -> targets.forEach(this::deleteQuietly));
        }
    }

    private void submit(Runnable task) {
        try {
            cleanupExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            // 큐(200)가 가득 찼습니다. 여기서 버리면 파일이 영영 남으므로 이 자리에서 실행합니다.
            // 호출자가 이미지 처리 스레드라면 그동안 붙잡히지만, 누수(영구)보다 지연(일시)을
            // 택한 것입니다 — 대안인 "버리고 로그"는 복구 수단이 없습니다.
            log.warn("이미지 정리를 큐에 넣지 못해 즉시 처리합니다.");
            task.run();
        }
    }

    /** 참조 확인이 일시 오류로 실패했을 때 배치를 다시 시도하는 횟수(첫 시도 포함). */
    private static final int MAX_ATTEMPTS = 3;

    private void deleteUnreferenced(List<String> imageUrls) {
        deleteUnreferenced(imageUrls, MAX_ATTEMPTS - 1);
    }

    /**
     * 목록에서 <b>아직 참조 중인 URL 을 배치 쿼리 두 번으로 걸러낸 뒤</b> 나머지를 지웁니다.
     *
     * <p>URL 마다 exists 를 두 번씩 날리면 장식장 통째 삭제(최대 수십 장)에서 단일 스레드
     * 실행기를 오래 점유합니다. in 절 두 번이면 끝나는 일입니다.
     *
     * <p><b>참조 확인이 실패하면 지우지 않습니다</b> — 확인 없이 지우는 쪽이 더 위험합니다.
     * 대신 배치를 큐 뒤로 다시 넣어 재시도하고(커넥션 풀 고갈 같은 일시 오류 대비), 끝내
     * 실패하면 수동 회수가 가능하도록 대상 URL 전체를 에러 로그로 남깁니다.
     */
    private void deleteUnreferenced(List<String> imageUrls, int retriesLeft) {
        Set<String> referenced;
        try {
            referenced = new HashSet<>(exhibitionItemRepository.findReferencedUrls(imageUrls));
            referenced.addAll(goodsImageRepository.findReferencedUrls(imageUrls));
        } catch (Exception e) {
            if (retriesLeft > 0) {
                log.warn("참조 확인 실패, 배치를 다시 시도합니다 ({}건, 남은 시도 {}): {}",
                        imageUrls.size(), retriesLeft, e.toString());
                submit(() -> deleteUnreferenced(imageUrls, retriesLeft - 1));
            } else {
                log.error("이미지 정리를 포기합니다. 수동 회수 대상: {}", imageUrls, e);
            }
            return;
        }

        for (String url : imageUrls) {
            if (referenced.contains(url)) {
                log.info("아직 참조 중이라 파일을 남깁니다: {}", url);
            } else {
                deleteQuietly(url);
            }
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
