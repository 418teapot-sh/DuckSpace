package com.duckspace.domain.exhibition.image;

import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 업로드를 <b>받아들이기 전에</b> 걸러야 하는 것들을 고정합니다.
 *
 * <p>여기서 안 걸리면 요청은 200 으로 접수되고(사용자는 성공한 줄 알고), 원본 바이트를 든 채
 * 큐를 차지했다가, 한참 뒤 백그라운드에서 이유도 없이 FAILED 로 끝납니다.
 */
class MultipartImageValidatorTest {

    private static byte[] pngBytes(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    /**
     * 실제로 거대한 이미지를 만들면 테스트가 못 돌아가므로 <b>헤더만</b> 크게 위조합니다.
     * IHDR 의 CRC 까지 다시 계산해야 리더가 크기 검사에 닿기 전에 깨진 청크로 죽지 않습니다.
     */
    private static byte[] hugePngHeader() throws IOException {
        byte[] png = pngBytes(8, 8);
        writeInt(png, 16, 20000);   // width
        writeInt(png, 20, 20000);   // height  → 4억 픽셀

        CRC32 crc = new CRC32();
        crc.update(png, 12, 4 + 13);
        writeInt(png, 12 + 4 + 13, (int) crc.getValue());
        return png;
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private static MockMultipartFile file(byte[] data) {
        return new MockMultipartFile("image", "goods.png", "image/png", data);
    }

    @Test
    @DisplayName("픽셀 수가 상한을 넘으면 업로드 시점에 거부한다")
    void 너무_큰_이미지는_접수하지_않는다() throws IOException {
        // 용량 제한(10MB)으로는 못 막습니다 — 고압축 PNG 는 몇 MB 로도 수천만 픽셀입니다.
        byte[] data = hugePngHeader();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> MultipartImageValidator.validate(file(data), data));

        assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.IMAGE_DIMENSION_TOO_LARGE);
    }

    @Test
    @DisplayName("보통 크기 사진은 그대로 통과한다")
    void 정상_이미지는_통과() throws IOException {
        byte[] data = pngBytes(200, 200);

        MultipartImageValidator.validate(file(data), data);   // 예외 없음
    }

    @Test
    @DisplayName("Content-Type 에 파라미터가 붙어 있어도 받아준다")
    void 파라미터가_붙은_헤더도_통과() throws IOException {
        // 일부 모바일 클라이언트가 image/jpeg; charset=UTF-8 처럼 보냅니다. 문자열 완전 일치로
        // 비교하면 바이트는 멀쩡한 이미지인데도 400 으로 거부하게 됩니다.
        byte[] data = pngBytes(50, 50);

        MultipartImageValidator.validate(
                new MockMultipartFile("image", "goods.png", "image/png; charset=UTF-8", data), data);

        MultipartImageValidator.validate(
                new MockMultipartFile("image", "goods.png", "IMAGE/PNG", data), data);
    }

    @Test
    @DisplayName("깨진 Content-Type 은 거부한다")
    void 깨진_헤더는_거부() throws IOException {
        byte[] data = pngBytes(50, 50);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> MultipartImageValidator.validate(
                        new MockMultipartFile("image", "goods.png", "image//png;;", data), data));

        assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    @DisplayName("헤더를 속여도 실제 바이트가 이미지가 아니면 거부한다")
    void 위장_파일은_거부() {
        byte[] data = "#!/bin/sh".getBytes();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> MultipartImageValidator.validate(file(data), data));

        assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    @DisplayName("픽셀 수는 디코딩 없이 헤더에서 읽는다")
    void 픽셀_수는_헤더로_센다() throws IOException {
        // 4억 픽셀짜리를 실제로 디코딩하면 테스트가 못 돌아갑니다. 헤더만 읽는다는 게 핵심입니다.
        assertThat(ImageInspector.pixelCount(hugePngHeader()).orElseThrow())
                .isEqualTo(20000L * 20000L);
        assertThat(ImageInspector.withinPixelLimit(hugePngHeader())).isFalse();
        assertThat(ImageInspector.withinPixelLimit(pngBytes(4000, 3000)))
                .as("폰 사진(1200만 화소)은 넉넉히 들어가야 합니다")
                .isTrue();
    }
}
