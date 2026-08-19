package com.duckspace.domain.exhibition.image;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * 이미지 바이트를 <b>내용 기준</b>으로 살펴봅니다.
 *
 * <p>파일명이나 {@code Content-Type} 헤더는 클라이언트가 마음대로 보내는 값이라 믿을 수 없습니다.
 * 여기서는 실제 바이트를 읽는 디코더가 무엇을 알아보는지로 판단합니다.
 *
 * <p>Spring 의존이 없어서 컨텍스트 없이 테스트할 수 있습니다.
 */
public final class ImageInspector {

    /**
     * 디코딩을 허용하는 최대 픽셀 수.
     *
     * <p>업로드 용량 제한(10MB)은 <b>디코딩 후 크기를 제한하지 못합니다.</b> 고압축 PNG 는
     * 몇 MB 로도 1억 픽셀짜리 캔버스가 될 수 있고, ARGB 로 펼치면 픽셀당 4바이트라
     * 수백 MB 를 잡아먹습니다. 4000x3000(1200만) 짜리 폰 사진이 넉넉히 들어가는 선입니다.
     */
    public static final long MAX_PIXELS = 40_000_000L;

    /** 굿즈 사진으로 받아줄 포맷. {@link #detectFormat} 이 돌려주는 이름과 같은 표기입니다. */
    public static final Set<String> SUPPORTED_FORMATS = Set.of("jpeg", "png");

    static {
        // 기본값(true)이면 ImageIO 가 읽고 쓸 때마다 임시파일을 거칩니다.
        // 이 파이프라인은 크기를 줄여 메모리 안에서 끝내도록 만든 것이라 디스크를 탈 이유가 없습니다.
        ImageIO.setUseCache(false);
    }

    private ImageInspector() {
    }

    /**
     * 바이트가 실제로 어떤 이미지 포맷인지 알아냅니다.
     *
     * @return 소문자 포맷명({@code "png"}, {@code "jpeg"}). 이미지가 아니면 비어 있음
     */
    public static Optional<String> detectFormat(byte[] data) {
        if (data == null || data.length == 0) {
            return Optional.empty();
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                // ImageIO 는 JPEG 를 "JPEG" 로 돌려주는데 MIME 타입은 image/jpeg 입니다.
                return Optional.of(format.equals("jpg") ? "jpeg" : format);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** {@link #detectFormat} 결과가 우리가 받아주는 포맷인지. */
    public static boolean isSupported(byte[] data) {
        return detectFormat(data).filter(SUPPORTED_FORMATS::contains).isPresent();
    }

    /**
     * <b>디코딩하지 않고</b> 헤더만 읽어 픽셀 수를 셉니다.
     *
     * <p>{@link #MAX_PIXELS} 확인이 {@link #read} 안에만 있으면 <b>백그라운드에서야</b>
     * 걸립니다. 그러면 요청은 200 으로 접수되고(사용자는 성공한 줄 알고), 원본 바이트를
     * 든 채 큐를 차지했다가, 한참 뒤 이유도 없이 FAILED 로 끝납니다.
     * 업로드 시점에 이걸 불러 400 으로 바로 알려주기 위한 것입니다.
     *
     * @return 픽셀 수. 이미지로 읽을 수 없으면 비어 있음(포맷 검사에서 이미 걸러집니다)
     */
    public static OptionalLong pixelCount(byte[] data) {
        if (data == null || data.length == 0) {
            return OptionalLong.empty();
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return OptionalLong.empty();
            }
            ImageReader reader = readers.next();
            reader.setInput(input);
            try {
                return OptionalLong.of((long) reader.getWidth(0) * reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return OptionalLong.empty();
        }
    }

    /** 픽셀 수가 상한 안인지. 크기를 읽을 수 없으면 여기서는 통과시킵니다(포맷 검사가 먼저 거릅니다). */
    public static boolean withinPixelLimit(byte[] data) {
        OptionalLong pixels = pixelCount(data);
        return pixels.isEmpty() || pixels.getAsLong() <= MAX_PIXELS;
    }

    /**
     * 바이트를 이미지로 읽습니다. <b>디코딩 전에 픽셀 수를 먼저 확인합니다.</b>
     *
     * <p>{@code ImageIO.read} 는 크기를 확인할 틈 없이 통째로 펼쳐버리므로, 헤더만 읽는
     * {@link ImageReader} 로 크기를 먼저 보고 거릅니다.
     *
     * @throws UncheckedIOException 이미지가 아니거나 너무 큰 경우
     */
    public static BufferedImage read(byte[] data) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("이미지로 읽을 수 없는 파일입니다.");
            }
            ImageReader reader = readers.next();
            reader.setInput(input);
            try {
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new IOException(
                            "이미지가 너무 큽니다: %d 픽셀 (허용 %d)".formatted(pixels, MAX_PIXELS));
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
