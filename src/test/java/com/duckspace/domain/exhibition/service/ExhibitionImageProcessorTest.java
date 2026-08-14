package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.exhibition.image.RemoveBgClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExhibitionImageProcessorTest {

    private static final Long ITEM_ID = 5L;
    private static final Long EXHIBITION_ID = 10L;

    @Mock
    private RemoveBgClient removeBgClient;

    @Mock
    private ImageStorage imageStorage;

    @Mock
    private ExhibitionItemStatusWriter statusWriter;

    private ExhibitionImageProcessor processor;
    private byte[] imageBytes;

    /** 테스트에서는 큐를 두지 않고 같은 스레드에서 바로 실행합니다. */
    private Executor directExecutor = Runnable::run;

    @BeforeEach
    void setUp() throws IOException {
        imageBytes = pngBytes(200, 200);
        processor = new ExhibitionImageProcessor(
                removeBgClient, imageStorage, statusWriter, task -> directExecutor.execute(task));
    }

    private byte[] pngBytes(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(w / 4, h / 4, w / 2, h / 2);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private void handle() {
        processor.handle(new ItemImageUploadedEvent(ITEM_ID, EXHIBITION_ID, imageBytes, "goods.png"));
    }

    @Test
    @DisplayName("배경 제거에 성공하면 READY 로 바뀌고 URL 이 채워진다")
    void 성공하면_READY() throws Exception {
        given(removeBgClient.isEnabled()).willReturn(true);
        given(removeBgClient.removeBackground(any(), anyString()))
                .willReturn(ImageIO.read(new ByteArrayInputStream(imageBytes)));
        given(imageStorage.upload(anyString(), any(), eq("image/png"))).willReturn("https://cdn/result.png");

        handle();

        verify(statusWriter).markReady(ITEM_ID, "https://cdn/result.png");
    }

    @Test
    @DisplayName("저장 키에 확장자가 붙는다")
    void 저장_키에_확장자가_붙는다() {
        given(removeBgClient.isEnabled()).willReturn(false);
        given(imageStorage.upload(anyString(), any(), eq("image/png"))).willReturn("https://cdn/result.png");

        handle();

        // 확장자가 없으면 로컬 서빙에서 Content-Type 을 정하지 못합니다.
        verify(imageStorage).upload(contains(".png"), any(), eq("image/png"));
    }

    @Test
    @DisplayName("API 키가 없으면 배경 제거를 건너뛰고 원본으로 처리한다")
    void 키가_없으면_원본으로_진행() {
        given(removeBgClient.isEnabled()).willReturn(false);
        given(imageStorage.upload(anyString(), any(), eq("image/png"))).willReturn("https://cdn/result.png");

        handle();

        verify(statusWriter).markReady(ITEM_ID, "https://cdn/result.png");
    }

    @Test
    @DisplayName("배경 제거가 실패해도 원본으로 계속 진행한다")
    void 배경제거_실패해도_READY() throws Exception {
        given(removeBgClient.isEnabled()).willReturn(true);
        given(removeBgClient.removeBackground(any(), anyString()))
                .willThrow(new IOException("remove.bg 402"));
        given(imageStorage.upload(anyString(), any(), eq("image/png"))).willReturn("https://cdn/result.png");

        handle();

        verify(statusWriter).markReady(ITEM_ID, "https://cdn/result.png");
    }

    @Test
    @DisplayName("중단 신호를 받으면 원본으로 진행하지 않고 인터럽트 상태를 되살린다")
    void 인터럽트는_삼키지_않는다() throws Exception {
        given(removeBgClient.isEnabled()).willReturn(true);
        given(removeBgClient.removeBackground(any(), anyString()))
                .willThrow(new InterruptedException("shutting down"));
        given(imageStorage.upload(anyString(), any(), anyString())).willReturn("https://cdn/original.png");

        try {
            handle();

            assertThat(Thread.currentThread().isInterrupted())
                    .as("인터럽트를 삼키면 종료 중이라는 신호가 사라집니다")
                    .isTrue();
            verify(statusWriter).markFailed(ITEM_ID, "https://cdn/original.png");
        } finally {
            // 다음 테스트로 인터럽트 상태가 새어나가지 않게 지웁니다.
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("저장에 실패하면 FAILED 로 남기되 원본은 살려둔다")
    void 저장_실패하면_FAILED() {
        given(removeBgClient.isEnabled()).willReturn(false);
        given(imageStorage.upload(anyString(), any(), anyString()))
                .willThrow(new RuntimeException("S3 down"))
                .willReturn("https://cdn/original.png");

        handle();

        verify(statusWriter).markFailed(ITEM_ID, "https://cdn/original.png");
    }

    @Test
    @DisplayName("JPEG 원본은 png 가 아니라 jpeg 로 저장한다")
    void 원본_포맷을_속이지_않는다() throws Exception {
        BufferedImage img = new BufferedImage(120, 120, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", out);
        imageBytes = out.toByteArray();

        given(removeBgClient.isEnabled()).willReturn(false);
        given(imageStorage.upload(anyString(), any(), eq("image/png")))
                .willThrow(new RuntimeException("S3 down"));
        given(imageStorage.upload(anyString(), any(), eq("image/jpeg")))
                .willReturn("https://cdn/original.jpeg");

        handle();

        // 실제 바이트는 JPEG 인데 image/png 로 저장하면 브라우저가 그리지 못합니다.
        verify(imageStorage).upload(contains(".jpeg"), any(), eq("image/jpeg"));
    }

    @Test
    @DisplayName("픽셀 수가 지나치게 큰 이미지는 디코딩하지 않고 실패로 남긴다")
    void 너무_큰_이미지는_거른다() throws Exception {
        // 파일 크기 제한(10MB)만으로는 디코딩 후 메모리를 막지 못합니다.
        // 실제로 4억 픽셀 이미지를 만들면 테스트가 못 돌아가므로 헤더만 크게 위조합니다.
        imageBytes = hugePngHeader();

        given(removeBgClient.isEnabled()).willReturn(false);
        given(imageStorage.upload(anyString(), any(), anyString())).willReturn("https://cdn/original.png");

        handle();

        // 처리된 이미지는 만들어지지 않고(= READY 없음), 원본만 남깁니다.
        verify(statusWriter, never()).markReady(anyLong(), anyString());
        verify(statusWriter).markFailed(ITEM_ID, "https://cdn/original.png");
    }

    /**
     * 폭·높이만 20000x20000(4억 픽셀)로 고쳐 쓴 PNG.
     *
     * <p>IHDR 청크의 CRC 까지 다시 계산합니다. CRC 를 맞추지 않으면 리더가 <b>크기 검사에
     * 닿기도 전에</b> 깨진 청크로 예외를 던져서, 테스트가 엉뚱한 이유로 통과합니다.
     */
    private byte[] hugePngHeader() throws IOException {
        byte[] png = pngBytes(8, 8);

        // PNG 레이아웃: 시그니처 8바이트 + [길이 4][타입 4]["IHDR" 데이터 13][CRC 4]
        // 데이터는 offset 16 부터 폭(4) 높이(4) ... 이고, CRC 는 타입+데이터에 대해 계산합니다.
        writeInt(png, 16, 20000);
        writeInt(png, 20, 20000);

        CRC32 crc = new CRC32();
        crc.update(png, 12, 4 + 13);          // "IHDR" + 데이터 13바이트
        writeInt(png, 12 + 4 + 13, (int) crc.getValue());

        return png;
    }

    private void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    @Test
    @DisplayName("큐가 가득 차 거절되면 PENDING 으로 두지 않고 FAILED 로 정리한다")
    void 큐가_가득_차면_FAILED() {
        directExecutor = task -> {
            throw new RejectedExecutionException("queue full");
        };

        handle();

        // 여기서 아무것도 안 하면 아이템이 영원히 PENDING 이라 프론트 폴링이 끝나지 않습니다.
        verify(statusWriter).markFailed(eq(ITEM_ID), isNull());
        verify(imageStorage, never()).upload(anyString(), any(), anyString());
    }

    // ------------------------------------------------------------------
    // 재처리
    // ------------------------------------------------------------------

    private static final String SOURCE_URL = "https://cdn/origin.png";

    private void handleRetry() {
        processor.handleRetry(new ItemImageRetryRequestedEvent(ITEM_ID, EXHIBITION_ID, SOURCE_URL));
    }

    @Test
    @DisplayName("재처리는 저장된 원본을 내려받아 다시 태운다")
    void 재처리는_원본을_다시_태운다() {
        given(removeBgClient.isEnabled()).willReturn(false);
        given(imageStorage.download(SOURCE_URL)).willReturn(imageBytes);
        given(imageStorage.upload(anyString(), any(), eq("image/png"))).willReturn("https://cdn/retried.png");

        handleRetry();

        verify(statusWriter).markReady(ITEM_ID, "https://cdn/retried.png");
    }

    @Test
    @DisplayName("재처리에 성공하면 남겨뒀던 원본을 지운다")
    void 재처리_성공하면_원본을_정리한다() {
        given(removeBgClient.isEnabled()).willReturn(false);
        given(imageStorage.download(SOURCE_URL)).willReturn(imageBytes);
        given(imageStorage.upload(anyString(), any(), eq("image/png"))).willReturn("https://cdn/retried.png");

        handleRetry();

        // 처리본이 원본을 대체했으므로 남겨둘 이유가 없습니다.
        verify(imageStorage).deleteByUrl(SOURCE_URL);
    }

    @Test
    @DisplayName("원본을 못 읽으면 주소를 지우지 않고 FAILED 로 되돌린다")
    void 원본을_못_읽어도_주소는_남긴다() {
        given(imageStorage.download(SOURCE_URL)).willThrow(new RuntimeException("S3 down"));

        handleRetry();

        // null 로 덮으면 다시 시도할 방법이 사라집니다.
        verify(statusWriter).markFailed(ITEM_ID, SOURCE_URL);
        verify(imageStorage, never()).deleteByUrl(anyString());
    }

    @Test
    @DisplayName("재처리가 실패해도 원본을 다시 저장하지 않고 기존 주소를 유지한다")
    void 재처리_실패해도_주소는_유지된다() {
        given(removeBgClient.isEnabled()).willReturn(false);
        given(imageStorage.download(SOURCE_URL)).willReturn(imageBytes);
        given(imageStorage.upload(anyString(), any(), anyString()))
                .willThrow(new RuntimeException("S3 down"));

        handleRetry();

        // 이미 저장돼 있는 원본이 있으므로 또 올릴 필요가 없습니다.
        verify(statusWriter).markFailed(ITEM_ID, SOURCE_URL);
    }

    @Test
    @DisplayName("재처리가 큐에서 거절돼도 원본 주소를 잃지 않는다")
    void 재처리_거절되어도_주소는_유지된다() {
        directExecutor = task -> {
            throw new RejectedExecutionException("queue full");
        };

        handleRetry();

        // 여기서 null 을 넘기면 재시도 버튼을 누를수록 복구가 불가능해집니다.
        verify(statusWriter).markFailed(ITEM_ID, SOURCE_URL);
    }
}
