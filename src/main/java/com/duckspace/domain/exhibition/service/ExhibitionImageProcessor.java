package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.image.GoodsImageProcessor;
import com.duckspace.domain.exhibition.image.ImageCleanup;
import com.duckspace.domain.exhibition.image.ImageInspector;
import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.exhibition.image.RemoveBgClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 업로드된 사진을 배경 제거 → 후처리 → 저장까지 <b>백그라운드에서</b> 처리합니다.
 *
 * <p>대상이 둘입니다 — 장식장 굿즈({@code ExhibitionItem})와 보관함 사진({@code GoodsImage}).
 * 파이프라인(누끼·후처리·저장·상태 기록·고아 회수)은 완전히 같고, <b>결과를 어디에 기록하고
 * 어느 경로에 저장하느냐만</b> 다릅니다. 그래서 {@link StatusRecorder} 로 대상만 갈아끼웁니다.
 *
 * <p>remove.bg 왕복만 1~3초, 후처리와 업로드까지 더하면 수 초가 걸립니다. 요청 스레드에서 하면
 * 업로드 버튼을 누른 화면이 그동안 멈춥니다. 그래서 접수만 하고 여기서 이어받습니다.
 *
 * <p><b>{@code @Async} 대신 실행기에 직접 넣는 이유:</b> 큐가 가득 차 거절되면
 * {@code @Async} 는 이벤트 리스너 바깥에서 예외를 던져 우리가 잡을 수 없고, 대상이
 * {@code PENDING} 인 채로 영원히 남습니다. 직접 넣으면 거절을 그 자리에서 잡아
 * {@code FAILED} 로 정리할 수 있습니다.
 *
 * <p><b>트랜잭션을 걸지 않는 이유:</b> 외부 API 왕복(최대 60초)을 트랜잭션이 물고 있으면
 * 그동안 DB 커넥션을 붙잡습니다. 결과를 쓸 때만 StatusWriter 가 짧은 트랜잭션을 엽니다.
 */
@Slf4j
@Component
public class ExhibitionImageProcessor {

    /** 출력 크기. 실제 사진으로 검증한 값입니다. remove.bg 무료 출력(0.25MP)에서 업스케일이 없습니다. */
    private static final int OUTPUT_SIZE = 384;

    private static final String PNG = "png";
    private static final String PNG_CONTENT_TYPE = "image/png";

    /** 재처리 때 remove.bg 에 보낼 파일명. 원본 파일명은 남겨두지 않습니다. */
    private static final String RETRY_FILE_NAME = "retry.png";

    /**
     * 처리 결과를 기록할 대상. 굿즈와 보관함 사진이 각자의 StatusWriter 를 감싸서 넘깁니다.
     *
     * <p>두 메서드 모두 <b>실제로 기록했는지</b>를 돌려줍니다. {@code false} 면 방금 올린
     * 이미지를 아무도 가리키지 않게 된 것이므로 호출부가 회수합니다.
     */
    private interface StatusRecorder {
        boolean ready(String url);

        boolean failed(String urlToKeep);
    }

    private final RemoveBgClient removeBgClient;
    private final ImageStorage imageStorage;
    private final ImageCleanup imageCleanup;
    private final ExhibitionItemStatusWriter itemStatusWriter;
    private final GoodsImageStatusWriter goodsImageStatusWriter;
    private final Executor imageExecutor;

    public ExhibitionImageProcessor(RemoveBgClient removeBgClient,
                                    ImageStorage imageStorage,
                                    ImageCleanup imageCleanup,
                                    ExhibitionItemStatusWriter itemStatusWriter,
                                    GoodsImageStatusWriter goodsImageStatusWriter,
                                    @Qualifier(ExhibitionAsyncConfig.IMAGE_EXECUTOR) Executor imageExecutor) {
        this.removeBgClient = removeBgClient;
        this.imageStorage = imageStorage;
        this.imageCleanup = imageCleanup;
        this.itemStatusWriter = itemStatusWriter;
        this.goodsImageStatusWriter = goodsImageStatusWriter;
        this.imageExecutor = imageExecutor;
    }

    // ------------------------------------------------------------------
    // 진입점 — 장식장 굿즈
    // ------------------------------------------------------------------

    /**
     * 저장 트랜잭션이 <b>커밋된 뒤에</b> 처리를 시작합니다.
     * 커밋 전에 시작하면 백그라운드 스레드가 아직 없는 행을 조회하게 됩니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ItemImageUploadedEvent event) {
        StatusRecorder recorder = itemRecorder(event.itemId());
        // 아직 저장해 둔 원본이 없으므로, 거절되면 남길 주소도 없습니다.
        submit(event.itemId(), null, recorder, () -> process(
                event.itemId(), itemKeyPrefix(event.exhibitionId()),
                event.imageData(), event.fileName(), null, recorder));
    }

    /** 실패한 굿즈 재처리. 원본 다운로드는 백그라운드에서 합니다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRetry(ItemImageRetryRequestedEvent event) {
        StatusRecorder recorder = itemRecorder(event.itemId());
        submitRetry(event.itemId(), event.sourceImageUrl(), recorder,
                itemKeyPrefix(event.exhibitionId()));
    }

    // ------------------------------------------------------------------
    // 진입점 — 보관함 사진
    // ------------------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(GoodsImageUploadedEvent event) {
        StatusRecorder recorder = imageRecorder(event.imageId());
        submit(event.imageId(), null, recorder, () -> process(
                event.imageId(), imageKeyPrefix(event.userId()),
                event.imageData(), event.fileName(), null, recorder));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRetry(GoodsImageRetryRequestedEvent event) {
        StatusRecorder recorder = imageRecorder(event.imageId());
        submitRetry(event.imageId(), event.sourceImageUrl(), recorder,
                imageKeyPrefix(event.userId()));
    }

    private StatusRecorder itemRecorder(Long itemId) {
        return new StatusRecorder() {
            public boolean ready(String url) {
                return itemStatusWriter.markReady(itemId, url);
            }

            public boolean failed(String keep) {
                return itemStatusWriter.markFailed(itemId, keep);
            }
        };
    }

    private StatusRecorder imageRecorder(Long imageId) {
        return new StatusRecorder() {
            public boolean ready(String url) {
                return goodsImageStatusWriter.markReady(imageId, url);
            }

            public boolean failed(String keep) {
                return goodsImageStatusWriter.markFailed(imageId, keep);
            }
        };
    }

    private static String itemKeyPrefix(Long exhibitionId) {
        return "exhibitions/" + exhibitionId;
    }

    private static String imageKeyPrefix(Long userId) {
        return "images/" + userId;
    }

    // ------------------------------------------------------------------
    // 공통 파이프라인
    // ------------------------------------------------------------------

    /**
     * 실행기에 넣되 거절을 직접 처리합니다.
     *
     * @param fallbackUrl 거절됐을 때 대상에 남겨둘 이미지 주소. 재처리면 원본 주소를 그대로
     *                    돌려놔야 합니다 — {@code null} 로 덮으면 다시 시도할 방법이 사라집니다.
     */
    private void submit(Long logId, String fallbackUrl, StatusRecorder recorder, Runnable task) {
        try {
            imageExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            // 큐까지 가득 찼습니다. 요청 스레드에서 대신 처리하면 "즉시 응답" 약속이 깨지므로,
            // 실패로 정리하고 사용자가 다시 시도하게 합니다.
            log.error("이미지 처리 큐가 가득 찼습니다. id={}", logId, e);
            recorder.failed(fallbackUrl);
        }
    }

    /** 재처리 공통 — 원본을 백그라운드에서 내려받아 파이프라인에 태웁니다. */
    private void submitRetry(Long logId, String sourceUrl, StatusRecorder recorder, String keyPrefix) {
        submit(logId, sourceUrl, recorder, () -> {
            byte[] data;
            try {
                data = imageStorage.download(sourceUrl);
            } catch (Exception e) {
                log.warn("재처리할 원본을 읽지 못했습니다. id={}, 원인={}", logId, e.toString());
                // 원본 주소는 그대로 둡니다. 지우면 다시 시도할 방법이 사라집니다.
                recorder.failed(sourceUrl);
                return;
            }
            process(logId, keyPrefix, data, RETRY_FILE_NAME, sourceUrl, recorder);
        });
    }

    /** 테스트에서 실행기를 거치지 않고 굿즈 처리를 바로 부를 수 있도록 열어둡니다. */
    void process(ItemImageUploadedEvent event) {
        process(event.itemId(), itemKeyPrefix(event.exhibitionId()),
                event.imageData(), event.fileName(), null, itemRecorder(event.itemId()));
    }

    /**
     * @param existingSourceUrl 재처리라면 이미 저장돼 있는 원본 주소. 처음 업로드면 {@code null}.
     */
    private void process(Long logId, String keyPrefix, byte[] data, String fileName,
                         String existingSourceUrl, StatusRecorder recorder) {
        try {
            BufferedImage source = removeBackgroundOrOriginal(data, fileName);
            BufferedImage processed = GoodsImageProcessor.process(
                    source, GoodsImageProcessor.Options.forExhibition(OUTPUT_SIZE));

            String url = imageStorage.upload(
                    keyFor(keyPrefix, "." + PNG), toBytes(processed), PNG_CONTENT_TYPE);

            boolean written;
            try {
                written = recorder.ready(url);
            } catch (Exception e) {
                // 기록에 실패해 방금 올린 처리본을 아무도 가리키지 않게 됐습니다.
                // 여기서 회수하지 않으면 DB 어디에도 주소가 없는 고아 객체로 영원히 남습니다.
                // (이 URL 은 방금 만든 것이라 참조 확인이 불필요합니다 — deleteOrphan)
                imageCleanup.deleteOrphan(url);
                throw e;
            }
            if (!written) {
                // 예외가 아니라 조용한 no-op(처리 중 삭제됨 · 이미 처리 끝남)도 같은 상황입니다.
                // 이때 기존 원본(existingSourceUrl)은 지우지 않습니다 — no-op 의 이유를 모르는 채로
                // 지우면, 이미 FAILED 로 끝난 대상이 가리키는 원본을 끊어버릴 수 있습니다.
                imageCleanup.deleteOrphan(url);
                return;
            }
            log.info("이미지 처리 완료. id={}", logId);

            // 처리본이 원본을 대체했으므로 남겨뒀던 원본은 지웁니다. 상태를 먼저 바꾼 뒤에
            // 지워야, 삭제가 실패해도 화면에는 정상 이미지가 보입니다.
            // 원본 URL 은 배치·보관함이 참조할 수 있어 가드가 있는 delete 를 씁니다.
            // (확인·삭제 모두 정리 실행기로 넘어가므로 처리 스레드를 붙잡지 않습니다)
            imageCleanup.delete(existingSourceUrl);

        } catch (InterruptedException e) {
            // 인터럽트는 "실패" 가 아니라 "그만두라는 신호" 입니다. 삼키면 종료가 지연되고,
            // 상위 코드가 스레드 상태를 보고 판단할 방법이 사라집니다.
            Thread.currentThread().interrupt();
            log.warn("이미지 처리가 중단되었습니다. id={}", logId);
            failKeepingSource(logId, keyPrefix, data, existingSourceUrl, recorder);

        } catch (Exception e) {
            log.warn("이미지 처리 실패. id={}, 원인={}", logId, e.toString());
            failKeepingSource(logId, keyPrefix, data, existingSourceUrl, recorder);
        }
    }

    /**
     * 실패로 기록하되 원본을 지키는 공통 뒷정리.
     *
     * <p>재처리였다면 이미 저장된 원본을 그대로 쓰고, 첫 업로드였다면 원본을 새로 저장합니다.
     * <b>기록이 no-op 으로 끝났고 원본을 방금 새로 올린 경우</b>에만 그 사본을 회수합니다 —
     * 기존 원본은 다른 상태(FAILED)가 가리키고 있을 수 있어 건드리지 않습니다.
     */
    private void failKeepingSource(Long logId, String keyPrefix, byte[] data,
                                   String existingSourceUrl, StatusRecorder recorder) {
        boolean freshlyStored = (existingSourceUrl == null);
        String keep = freshlyStored ? storeOriginalQuietly(logId, keyPrefix, data) : existingSourceUrl;

        boolean written;
        try {
            written = recorder.failed(keep);
        } catch (Exception e) {
            log.warn("실패 기록도 실패했습니다. id={}", logId, e);
            written = false;
        }
        if (!written && freshlyStored && keep != null) {
            // 방금 새로 저장한 원본 사본이라 아무 행도 가리킨 적 없습니다 — 참조 확인 불필요.
            imageCleanup.deleteOrphan(keep);
        }
    }

    /**
     * 배경을 제거합니다. 키가 없거나 호출이 실패하면 원본을 그대로 씁니다.
     *
     * <p>배경 제거는 외부 API 라 언제든 실패할 수 있는데, 그것 때문에 업로드 자체가
     * 실패하면 안 됩니다. 배경이 남은 사진이라도 남기는 편이 낫습니다.
     *
     * <p>단 {@link InterruptedException} 은 예외입니다. 서버가 내려가는 중이라는 뜻이므로
     * 원본으로 계속 진행하지 않고 그대로 올려보냅니다.
     */
    private BufferedImage removeBackgroundOrOriginal(byte[] imageData, String fileName)
            throws Exception {
        if (!removeBgClient.isEnabled()) {
            return ImageInspector.read(imageData);
        }
        try {
            return removeBgClient.removeBackground(imageData, fileName);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("배경 제거에 실패해 원본으로 진행합니다: {}", e.toString());
            return ImageInspector.read(imageData);
        }
    }

    /** 실패했더라도 사용자가 올린 사진은 잃지 않도록 원본이라도 저장해 둡니다. */
    private String storeOriginalQuietly(Long logId, String keyPrefix, byte[] data) {
        try {
            // 원본이 JPEG 인데 .png 로 저장하면 실제 바이트와 확장자·Content-Type 이 어긋나
            // 브라우저가 그림을 그리지 못합니다. 바이트에서 실제 포맷을 읽어 맞춥니다.
            String format = ImageInspector.detectFormat(data).orElse(PNG);
            return imageStorage.upload(
                    keyFor(keyPrefix, "-origin." + format), data, "image/" + format);
        } catch (Exception e) {
            log.warn("원본 저장도 실패했습니다. id={}", logId);
            return null;
        }
    }

    private static String keyFor(String keyPrefix, String suffix) {
        return "%s/%s%s".formatted(keyPrefix, UUID.randomUUID(), suffix);
    }

    private static byte[] toBytes(RenderedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, PNG, out);
        return out.toByteArray();
    }
}
