package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.image.GoodsImageProcessor;
import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.exhibition.image.RemoveBgClient;
import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.image.RenderedImage;
import java.util.UUID;

/**
 * 업로드된 사진을 배경 제거 → 후처리 → 저장까지 <b>백그라운드에서</b> 처리합니다.
 *
 * <p>remove.bg 왕복만 1~3초, 후처리와 업로드까지 더하면 수 초가 걸립니다. 요청 스레드에서 하면
 * 업로드 버튼을 누른 화면이 그동안 멈춥니다. 그래서 접수만 하고 여기서 이어받습니다.
 *
 * <p><b>{@link ExhibitionItemService} 와 별도 빈인 이유:</b> 같은 빈 안에서 호출하면
 * {@code @Async} 프록시를 타지 않아 그냥 동기 실행됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExhibitionImageProcessor {

    /** 슬롯 출력 크기. 실제 사진으로 검증한 값입니다. remove.bg 무료 출력(0.25MP)에서 업스케일이 없습니다. */
    private static final int OUTPUT_SIZE = 384;
    private static final String PNG = "png";
    private static final String PNG_CONTENT_TYPE = "image/png";

    private final RemoveBgClient removeBgClient;
    private final ImageStorage imageStorage;
    private final ExhibitionItemRepository exhibitionItemRepository;

    /**
     * 저장 트랜잭션이 <b>커밋된 뒤에</b> 처리를 시작합니다.
     *
     * <p>커밋 전에 시작하면 백그라운드 스레드가 아직 없는 아이템을 조회하게 됩니다.
     * 커밋이 끝난 시점이라 새 트랜잭션이 필요합니다({@code REQUIRES_NEW}).
     */
    @Async(ExhibitionAsyncConfig.IMAGE_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(ItemImageUploadedEvent event) {
        process(event.itemId(), event.imageData(), event.fileName());
    }

    private void process(Long itemId, byte[] imageData, String fileName) {
        ExhibitionItem item = exhibitionItemRepository.findById(itemId).orElse(null);
        if (item == null) {
            // 처리 중에 사용자가 삭제했을 수 있습니다. 실패가 아니라 정상 흐름입니다.
            log.info("처리할 아이템이 이미 없습니다. itemId={}", itemId);
            return;
        }

        try {
            BufferedImage source = removeBackgroundOrOriginal(imageData, fileName);
            BufferedImage processed = GoodsImageProcessor.process(
                    source, GoodsImageProcessor.Options.forExhibition(OUTPUT_SIZE));

            String url = imageStorage.upload(
                    keyFor(item, PNG), toBytes(processed), PNG_CONTENT_TYPE);

            item.markReady(url);
            log.info("이미지 처리 완료. itemId={}", itemId);

        } catch (Exception e) {
            log.warn("이미지 처리 실패. itemId={}, 원인={}", itemId, e.toString());
            item.markFailed(storeOriginalQuietly(item, imageData));
        }
    }

    /**
     * 배경을 제거합니다. 키가 없거나 호출이 실패하면 원본을 그대로 씁니다.
     *
     * <p>배경 제거는 외부 API 라 언제든 실패할 수 있는데, 그것 때문에 업로드 자체가
     * 실패하면 안 됩니다. 배경이 남은 사진이라도 남기는 편이 낫습니다.
     */
    private BufferedImage removeBackgroundOrOriginal(byte[] imageData, String fileName) throws Exception {
        if (!removeBgClient.isEnabled()) {
            return readImage(imageData);
        }
        try {
            return removeBgClient.removeBackground(imageData, fileName);
        } catch (Exception e) {
            log.warn("배경 제거에 실패해 원본으로 진행합니다: {}", e.toString());
            return readImage(imageData);
        }
    }

    /** 실패했더라도 사용자가 올린 사진은 잃지 않도록 원본이라도 저장해 둡니다. */
    private String storeOriginalQuietly(ExhibitionItem item, byte[] imageData) {
        try {
            return imageStorage.upload(keyFor(item, "origin.png"), imageData, PNG_CONTENT_TYPE);
        } catch (Exception e) {
            log.warn("원본 저장도 실패했습니다. itemId={}", item.getId());
            return null;
        }
    }

    private static BufferedImage readImage(byte[] data) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) {
            throw new IllegalArgumentException("이미지로 읽을 수 없는 파일입니다.");
        }
        return image;
    }

    private static String keyFor(ExhibitionItem item, String suffix) {
        return "exhibitions/%d/%s-%s".formatted(
                item.getExhibition().getId(), UUID.randomUUID(), suffix);
    }

    private static byte[] toBytes(RenderedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, PNG, out);
        return out.toByteArray();
    }
}
