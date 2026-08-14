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
 * <p>remove.bg 왕복만 1~3초, 후처리와 업로드까지 더하면 수 초가 걸립니다. 요청 스레드에서 하면
 * 업로드 버튼을 누른 화면이 그동안 멈춥니다. 그래서 접수만 하고 여기서 이어받습니다.
 *
 * <p><b>{@code @Async} 대신 실행기에 직접 넣는 이유:</b> 큐가 가득 차 거절되면
 * {@code @Async} 는 이벤트 리스너 바깥에서 예외를 던져 우리가 잡을 수 없고, 아이템이
 * {@code PENDING} 인 채로 영원히 남습니다. 직접 넣으면 거절을 그 자리에서 잡아
 * {@code FAILED} 로 정리할 수 있습니다.
 *
 * <p><b>트랜잭션을 걸지 않는 이유:</b> 외부 API 왕복(최대 60초)을 트랜잭션이 물고 있으면
 * 그동안 DB 커넥션을 붙잡습니다. 결과를 쓸 때만 {@link ExhibitionItemStatusWriter} 가
 * 짧은 트랜잭션을 엽니다.
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

    private final RemoveBgClient removeBgClient;
    private final ImageStorage imageStorage;
    private final ImageCleanup imageCleanup;
    private final ExhibitionItemStatusWriter statusWriter;
    private final Executor imageExecutor;

    public ExhibitionImageProcessor(RemoveBgClient removeBgClient,
                                    ImageStorage imageStorage,
                                    ImageCleanup imageCleanup,
                                    ExhibitionItemStatusWriter statusWriter,
                                    @Qualifier(ExhibitionAsyncConfig.IMAGE_EXECUTOR) Executor imageExecutor) {
        this.removeBgClient = removeBgClient;
        this.imageStorage = imageStorage;
        this.imageCleanup = imageCleanup;
        this.statusWriter = statusWriter;
        this.imageExecutor = imageExecutor;
    }

    /**
     * 저장 트랜잭션이 <b>커밋된 뒤에</b> 처리를 시작합니다.
     *
     * <p>커밋 전에 시작하면 백그라운드 스레드가 아직 없는 아이템을 조회하게 됩니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ItemImageUploadedEvent event) {
        // 아직 저장해 둔 원본이 없으므로, 거절되면 남길 주소도 없습니다.
        submit(event.itemId(), null, () -> process(event));
    }

    /**
     * 실패한 굿즈 재처리. 원본을 <b>여기(백그라운드)에서</b> 내려받습니다.
     *
     * <p>요청 스레드에서 받으면 S3 왕복만큼 응답이 늦어지고, 그건 업로드에서 피하려던 것과
     * 같은 문제입니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRetry(ItemImageRetryRequestedEvent event) {
        String source = event.sourceImageUrl();
        submit(event.itemId(), source, () -> {
            byte[] data;
            try {
                data = imageStorage.download(source);
            } catch (Exception e) {
                log.warn("재처리할 원본을 읽지 못했습니다. itemId={}, 원인={}", event.itemId(), e.toString());
                // 원본 주소는 그대로 둡니다. 지우면 다시 시도할 방법이 사라집니다.
                statusWriter.markFailed(event.itemId(), source);
                return;
            }
            process(new ItemImageUploadedEvent(
                    event.itemId(), event.exhibitionId(), data, RETRY_FILE_NAME), source);
        });
    }

    /**
     * 실행기에 넣되 거절을 직접 처리합니다.
     *
     * @param fallbackUrl 거절됐을 때 아이템에 남겨둘 이미지 주소. 재처리면 원본 주소를 그대로
     *                    돌려놔야 합니다 — {@code null} 로 덮으면 다시 시도할 방법이 사라집니다.
     */
    private void submit(Long itemId, String fallbackUrl, Runnable task) {
        try {
            imageExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            // 큐까지 가득 찼습니다. 요청 스레드에서 대신 처리하면 "즉시 응답" 약속이 깨지므로,
            // 실패로 정리하고 사용자가 다시 시도하게 합니다.
            log.error("이미지 처리 큐가 가득 찼습니다. itemId={}", itemId, e);
            statusWriter.markFailed(itemId, fallbackUrl);
        }
    }

    /** 실제 처리. 테스트에서 실행기를 거치지 않고 바로 부를 수 있도록 열어둡니다. */
    void process(ItemImageUploadedEvent event) {
        process(event, null);
    }

    /**
     * @param existingSourceUrl 재처리라면 이미 저장돼 있는 원본 주소. 처음 업로드면 {@code null}.
     */
    private void process(ItemImageUploadedEvent event, String existingSourceUrl) {
        Long itemId = event.itemId();
        try {
            BufferedImage source = removeBackgroundOrOriginal(event.imageData(), event.fileName());
            BufferedImage processed = GoodsImageProcessor.process(
                    source, GoodsImageProcessor.Options.forExhibition(OUTPUT_SIZE));

            String url = imageStorage.upload(
                    keyFor(event.exhibitionId(), "." + PNG), toBytes(processed), PNG_CONTENT_TYPE);

            try {
                statusWriter.markReady(itemId, url);
            } catch (Exception e) {
                // 방금 올린 처리본을 아무도 가리키지 않게 됐습니다. 여기서 회수하지 않으면
                // DB 어디에도 주소가 없는 고아 객체로 영원히 남습니다.
                imageCleanup.delete(url);
                throw e;
            }
            log.info("이미지 처리 완료. itemId={}", itemId);

            // 처리본이 원본을 대체했으므로 남겨뒀던 원본은 지웁니다. 상태를 먼저 바꾼 뒤에
            // 지워야, 삭제가 실패해도 화면에는 정상 이미지가 보입니다.
            imageCleanup.delete(existingSourceUrl);

        } catch (InterruptedException e) {
            // 인터럽트는 "실패" 가 아니라 "그만두라는 신호" 입니다. 삼키면 종료가 지연되고,
            // 상위 코드가 스레드 상태를 보고 판단할 방법이 사라집니다.
            Thread.currentThread().interrupt();
            log.warn("이미지 처리가 중단되었습니다. itemId={}", itemId);
            statusWriter.markFailed(itemId, sourceToKeep(event, existingSourceUrl));

        } catch (Exception e) {
            log.warn("이미지 처리 실패. itemId={}, 원인={}", itemId, e.toString());
            statusWriter.markFailed(itemId, sourceToKeep(event, existingSourceUrl));
        }
    }

    /** 재처리였다면 이미 저장된 원본을 그대로 두고, 첫 업로드였다면 원본을 새로 저장합니다. */
    private String sourceToKeep(ItemImageUploadedEvent event, String existingSourceUrl) {
        return existingSourceUrl != null ? existingSourceUrl : storeOriginalQuietly(event);
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
    private String storeOriginalQuietly(ItemImageUploadedEvent event) {
        try {
            // 원본이 JPEG 인데 .png 로 저장하면 실제 바이트와 확장자·Content-Type 이 어긋나
            // 브라우저가 그림을 그리지 못합니다. 바이트에서 실제 포맷을 읽어 맞춥니다.
            String format = ImageInspector.detectFormat(event.imageData()).orElse(PNG);
            return imageStorage.upload(
                    keyFor(event.exhibitionId(), "-origin." + format),
                    event.imageData(),
                    "image/" + format);
        } catch (Exception e) {
            log.warn("원본 저장도 실패했습니다. itemId={}", event.itemId());
            return null;
        }
    }

    private static String keyFor(Long exhibitionId, String suffix) {
        return "exhibitions/%d/%s%s".formatted(exhibitionId, UUID.randomUUID(), suffix);
    }

    private static byte[] toBytes(RenderedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, PNG, out);
        return out.toByteArray();
    }
}
