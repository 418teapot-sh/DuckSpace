package com.duckspace.domain.exhibition.image;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 굿즈 사진을 장식장에 얹을 수 있는 형태로 정규화합니다.
 *
 * <p><b>의도적으로 순수 Java 로만 작성되어 있습니다.</b> Spring·외부 라이브러리 의존이 없어
 * 단위 테스트에서 컨텍스트 없이 바로 돌릴 수 있습니다.
 *
 * <p>처리 순서
 * <pre>
 *   1) 작업 크기로 리사이즈   메모리 보호 (4000x3000 원본은 펼치면 한 장에 48MB)
 *   2) 밝기 보정             어둡게/밝게 찍힌 편차를 흡수 (감마 — 하이라이트 보존)
 *   3) 화이트밸런스          기본은 끔. 굿즈 고유색이 바뀌면 안 되기 때문
 *   4) 채도 부스트           화면에서 작게 보일 때 또렷하도록
 *   5) 투명 여백 트림        피사체에 딱 맞게 크롭
 *   6) 흰색 아웃라인         <b>전시에서는 사용하지 않습니다</b> (0)
 *   7) 정사각 캔버스 배치     굿즈끼리 크기 기준 통일
 * </pre>
 *
 * <p>2~4번은 알파가 없는 일반 JPG 에도 동작합니다.
 * 5~6번은 <b>배경이 제거되어 알파 채널이 있는 이미지</b>여야 의미가 있습니다.
 *
 * <p>파라미터는 실제 굿즈 사진(0.25MP remove.bg 출력)으로 검증했습니다.
 * 흰 테두리를 넣으면 장식장 위에서 오려붙인 스티커처럼 보이고, 출력이 384px 을 넘으면
 * 업스케일이 시작됩니다.
 */
public final class GoodsImageProcessor {

    /** 통계 계산 시 "피사체"로 간주할 최소 알파값. 반투명 가장자리를 제외합니다. */
    private static final int OPAQUE_THRESHOLD = 16;

    /** 트림 시 살릴 최소 알파값. */
    private static final int TRIM_THRESHOLD = 10;

    /** 자동 레벨에서 잘라낼 상·하위 비율. 먼지/하이라이트 몇 픽셀에 휘둘리지 않게 합니다. */
    private static final double CLIP_RATIO = 0.005;

    /** 화이트패치 보정에서 "조명색"으로 볼 상위 밝기 픽셀 비율. */
    private static final double WHITE_PATCH_RATIO = 0.01;

    /** 감마 밝기 보정이 맞추려는 목표 평균 밝기. 너무 높이면 밝은 굿즈가 날아갑니다. */
    private static final double TARGET_MEAN_LUMINANCE = 165.0;

    /**
     * 밝기 보정 방식.
     *
     * <p><b>굿즈 사진에는 {@link #TARGET_MEAN} 을 권장합니다.</b>
     * <ul>
     *   <li>{@link #AUTO_LEVELS} 는 히스토그램을 0~255 로 꽉 늘립니다. 어두운 사진에는 좋지만,
     *       <b>크림색·흰색 계열 굿즈</b>는 가장 밝은 부분이 255 로 밀려 <b>하얗게 날아갑니다.</b></li>
     *   <li>{@link #TARGET_MEAN} 은 감마 곡선으로 평균 밝기만 맞춥니다. 0 과 255 는 그대로 두므로
     *       <b>밝은 부분이 타지 않습니다.</b></li>
     * </ul>
     */
    public enum BrightnessMode {
        /** 보정하지 않음. */
        NONE,
        /** 감마로 평균 밝기만 맞춤. 하이라이트 보존. 굿즈 사진에 적합. */
        TARGET_MEAN,
        /** 히스토그램 풀 스트레치. 밝은 피사체는 날아갈 수 있음. */
        AUTO_LEVELS
    }

    /**
     * 화이트밸런스 방식.
     *
     * <p><b>굿즈 사진에는 {@link #WHITE_PATCH} 를 권장합니다.</b> 이유는 아래와 같습니다.
     * <ul>
     *   <li>{@link #GRAY_WORLD} 는 "화면 전체 평균이 회색"이라고 가정합니다. 풍경 사진에는 맞지만,
     *       배경을 오려낸 <b>단색 위주 굿즈</b>에는 맞지 않습니다. 갈색 키링을 회색으로 만들려다
     *       올리브색으로 틀어버리는 식으로 <b>피사체 고유색을 파괴</b>합니다.</li>
     *   <li>{@link #WHITE_PATCH} 는 "가장 밝은 부분이 조명색"이라고 가정하고 그 부분만 중성화합니다.
     *       조명 색온도만 걷어내고 굿즈 색은 유지됩니다.</li>
     * </ul>
     */
    public enum WhiteBalanceMode {
        /** 보정하지 않음. */
        NONE,
        /** 전체 평균을 회색으로. 풍경용. 단색 굿즈에서는 색이 크게 틀어집니다. */
        GRAY_WORLD,
        /** 최상위 밝기 영역만 중성화. 굿즈 사진에 적합. */
        WHITE_PATCH
    }

    /**
     * 처리 파라미터. 값을 바꿔가며 결과를 비교하는 것이 이 프로젝트의 목적입니다.
     *
     * @param maxWorkingSize  처리 전 리사이즈할 긴 변 (px). 메모리 보호용
     * @param outputSize      최종 정사각 한 변 (px)
     * @param padding         정사각 캔버스 안쪽 여백 (px)
     * @param outlineWidth    흰 테두리 두께 (px). 0 이면 끔
     * @param saturationBoost 채도 배율. 1.0 = 원본
     * @param brightness      밝기 보정 방식
     * @param whiteBalance    화이트밸런스 방식
     */
    public record Options(
            int maxWorkingSize,
            int outputSize,
            int padding,
            int outlineWidth,
            float saturationBoost,
            BrightnessMode brightness,
            WhiteBalanceMode whiteBalance
    ) {
        /**
         * 전시(장식장)용 프리셋.
         *
         * <p>도감(띠부씰)과 달리 <b>흰 테두리를 넣지 않습니다.</b> 실제 책장·협탁 배경 위에 얹는 그림이라
         * 스티커 테두리가 붙으면 오려붙인 것처럼 보입니다.
         *
         * <p>여백도 줄입니다. 화면에서 작게 보이므로 피사체가 캔버스를 최대한 채워야 합니다.
         */
        public static Options forExhibition(int outputSize) {
            return new Options(1024, outputSize, 8, 0, 1.10f,
                    BrightnessMode.TARGET_MEAN, WhiteBalanceMode.NONE);
        }

        public Options withOutlineWidth(int w) {
            return new Options(maxWorkingSize, outputSize, padding, w, saturationBoost, brightness, whiteBalance);
        }

        public Options withSaturationBoost(float s) {
            return new Options(maxWorkingSize, outputSize, padding, outlineWidth, s, brightness, whiteBalance);
        }

        public Options withWhiteBalance(WhiteBalanceMode mode) {
            return new Options(maxWorkingSize, outputSize, padding, outlineWidth, saturationBoost, brightness, mode);
        }

        public Options withBrightness(BrightnessMode mode) {
            return new Options(maxWorkingSize, outputSize, padding, outlineWidth, saturationBoost, mode, whiteBalance);
        }
    }

    private GoodsImageProcessor() {
    }

    /**
     * 전체 파이프라인을 실행합니다.
     *
     * <p>단계 순서와 각 단계의 근거는 클래스 주석을 참고하세요. 파라미터는 실제 굿즈 사진으로
     * 검증했습니다. (전시는 흰 테두리 없음 / 출력 384px)
     */
    public static BufferedImage process(BufferedImage src, Options o) {
        BufferedImage img = resizeToFit(toArgb(src), o.maxWorkingSize());

        img = switch (o.brightness()) {
            case AUTO_LEVELS -> autoLevels(img);
            case TARGET_MEAN -> normalizeBrightness(img);
            case NONE -> img;
        };
        img = switch (o.whiteBalance()) {
            case GRAY_WORLD -> grayWorldWhiteBalance(img);
            case WHITE_PATCH -> whitePatchWhiteBalance(img);
            case NONE -> img;
        };
        if (o.saturationBoost() != 1.0f) {
            img = adjustSaturation(img, o.saturationBoost());
        }

        img = trimTransparent(img);

        if (o.outlineWidth() > 0) {
            img = addWhiteOutline(img, o.outlineWidth());
        }

        return fitToSquare(img, o.outputSize(), o.padding());
    }

    // ------------------------------------------------------------------
    // 개별 단계 (각각 따로 써도 됩니다)
    // ------------------------------------------------------------------

    /** 긴 변이 maxDim 을 넘으면 비율을 유지한 채 축소합니다. */
    public static BufferedImage resizeToFit(BufferedImage src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longSide = Math.max(w, h);
        if (longSide <= maxDim) {
            return src;
        }
        double scale = (double) maxDim / longSide;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));

        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        applyQualityHints(g);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    /**
     * 자동 레벨 보정. 밝기 히스토그램의 상·하위 {@value #CLIP_RATIO} 를 잘라내고
     * 나머지를 0~255 로 늘립니다. 어둡게 찍힌 사진이 눈에 띄게 살아납니다.
     *
     * <p>세 채널에 동일한 구간을 적용하므로 색상(hue)은 유지됩니다.
     * 색온도 보정은 {@link #grayWorldWhiteBalance} 가 담당합니다.
     */
    public static BufferedImage autoLevels(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);

        long[] hist = new long[256];
        long count = 0;
        for (int p : px) {
            if (((p >>> 24) & 0xFF) < OPAQUE_THRESHOLD) {
                continue;
            }
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            int lum = (r * 299 + g * 587 + b * 114) / 1000;
            hist[lum]++;
            count++;
        }
        if (count == 0) {
            return src;
        }

        long clip = (long) (count * CLIP_RATIO);
        int lo = 0;
        int hi = 255;
        long acc = 0;
        for (int i = 0; i < 256; i++) {
            acc += hist[i];
            if (acc > clip) {
                lo = i;
                break;
            }
        }
        acc = 0;
        for (int i = 255; i >= 0; i--) {
            acc += hist[i];
            if (acc > clip) {
                hi = i;
                break;
            }
        }
        if (hi - lo < 8) {
            return src;   // 이미 평탄하거나 단색 — 건드리면 오히려 망가집니다
        }

        int[] lut = new int[256];
        double scale = 255.0 / (hi - lo);
        for (int i = 0; i < 256; i++) {
            lut[i] = clamp((int) Math.round((i - lo) * scale));
        }

        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int a = (p >>> 24) & 0xFF;
            out[i] = (a << 24)
                    | (lut[(p >> 16) & 0xFF] << 16)
                    | (lut[(p >> 8) & 0xFF] << 8)
                    | lut[p & 0xFF];
        }
        return fromPixels(out, w, h);
    }

    /**
     * 감마 곡선으로 평균 밝기만 목표치에 맞춥니다. <b>굿즈 사진에는 이쪽을 권장합니다.</b>
     *
     * <p>{@link #autoLevels} 와 달리 0 과 255 를 고정한 채 중간 톤만 끌어올리므로,
     * 크림색·흰색 굿즈가 하얗게 날아가지 않습니다.
     */
    public static BufferedImage normalizeBrightness(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);

        long sum = 0;
        long count = 0;
        for (int p : px) {
            if (((p >>> 24) & 0xFF) < OPAQUE_THRESHOLD) {
                continue;
            }
            sum += luminance(p);
            count++;
        }
        if (count == 0) {
            return src;
        }

        double mean = (double) sum / count;
        if (mean < 1 || mean > 254) {
            return src;
        }
        // gamma = log(목표) / log(현재).  결과가 목표 평균에 가까워집니다.
        double gamma = Math.log(TARGET_MEAN_LUMINANCE / 255.0) / Math.log(mean / 255.0);
        gamma = clamp(gamma, 0.65, 1.5);
        if (Math.abs(gamma - 1.0) < 0.02) {
            return src;
        }

        int[] lut = new int[256];
        for (int i = 0; i < 256; i++) {
            lut[i] = clamp((int) Math.round(255.0 * Math.pow(i / 255.0, gamma)));
        }

        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int a = (p >>> 24) & 0xFF;
            out[i] = (a << 24)
                    | (lut[(p >> 16) & 0xFF] << 16)
                    | (lut[(p >> 8) & 0xFF] << 8)
                    | lut[p & 0xFF];
        }
        return fromPixels(out, w, h);
    }

    /**
     * 그레이월드 가정 기반 화이트밸런스. 이미지 전체 평균색이 회색이라고 보고
     * 채널별 이득을 맞춥니다. 형광등 아래 노랗게 찍힌 사진을 중성화합니다.
     */
    public static BufferedImage grayWorldWhiteBalance(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);

        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        long count = 0;
        for (int p : px) {
            if (((p >>> 24) & 0xFF) < OPAQUE_THRESHOLD) {
                continue;
            }
            sumR += (p >> 16) & 0xFF;
            sumG += (p >> 8) & 0xFF;
            sumB += p & 0xFF;
            count++;
        }
        if (count == 0) {
            return src;
        }

        double mR = (double) sumR / count;
        double mG = (double) sumG / count;
        double mB = (double) sumB / count;
        double target = (mR + mG + mB) / 3.0;
        if (target < 1) {
            return src;
        }

        // 과보정 방지. 색이 강한 단색 굿즈에서 이득이 폭주하는 것을 막습니다.
        double gR = clampGain(target / Math.max(mR, 1));
        double gG = clampGain(target / Math.max(mG, 1));
        double gB = clampGain(target / Math.max(mB, 1));

        int[] lutR = gainLut(gR);
        int[] lutG = gainLut(gG);
        int[] lutB = gainLut(gB);

        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int a = (p >>> 24) & 0xFF;
            out[i] = (a << 24)
                    | (lutR[(p >> 16) & 0xFF] << 16)
                    | (lutG[(p >> 8) & 0xFF] << 8)
                    | lutB[p & 0xFF];
        }
        return fromPixels(out, w, h);
    }

    /**
     * 화이트패치 기반 화이트밸런스. <b>굿즈 사진에는 이쪽을 권장합니다.</b>
     *
     * <p>이미지에서 가장 밝은 상위 {@value #WHITE_PATCH_RATIO} 영역이 조명색을 반영한다고 보고,
     * 그 부분만 중성 회색이 되도록 채널 이득을 맞춥니다. 조명 색온도만 걷어내므로
     * 그레이월드와 달리 <b>피사체 고유색이 유지</b>됩니다.
     */
    public static BufferedImage whitePatchWhiteBalance(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);

        long[] hist = new long[256];
        long count = 0;
        for (int p : px) {
            if (((p >>> 24) & 0xFF) < OPAQUE_THRESHOLD) {
                continue;
            }
            hist[luminance(p)]++;
            count++;
        }
        if (count < 50) {
            return src;
        }

        // 상위 밝기 구간의 하한선을 찾습니다.
        long want = Math.max(20, (long) (count * WHITE_PATCH_RATIO));
        int threshold = 255;
        long acc = 0;
        for (int i = 255; i >= 0; i--) {
            acc += hist[i];
            if (acc >= want) {
                threshold = i;
                break;
            }
        }

        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        long patch = 0;
        for (int p : px) {
            if (((p >>> 24) & 0xFF) < OPAQUE_THRESHOLD || luminance(p) < threshold) {
                continue;
            }
            sumR += (p >> 16) & 0xFF;
            sumG += (p >> 8) & 0xFF;
            sumB += p & 0xFF;
            patch++;
        }
        if (patch == 0) {
            return src;
        }

        double mR = (double) sumR / patch;
        double mG = (double) sumG / patch;
        double mB = (double) sumB / patch;
        double target = (mR + mG + mB) / 3.0;
        if (target < 1) {
            return src;
        }

        // 그레이월드보다 훨씬 좁게 제한합니다. 굿즈 색이 틀어지는 것을 막는 안전장치입니다.
        int[] lutR = gainLut(clamp(target / Math.max(mR, 1), 0.85, 1.20));
        int[] lutG = gainLut(clamp(target / Math.max(mG, 1), 0.85, 1.20));
        int[] lutB = gainLut(clamp(target / Math.max(mB, 1), 0.85, 1.20));

        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int a = (p >>> 24) & 0xFF;
            out[i] = (a << 24)
                    | (lutR[(p >> 16) & 0xFF] << 16)
                    | (lutG[(p >> 8) & 0xFF] << 8)
                    | lutB[p & 0xFF];
        }
        return fromPixels(out, w, h);
    }

    /** 채도 조절. factor 1.0 이 원본, 1.2 정도면 스티커답게 살아납니다. */
    public static BufferedImage adjustSaturation(BufferedImage src, float factor) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);
        int[] out = new int[px.length];

        float[] hsb = new float[3];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int a = (p >>> 24) & 0xFF;
            if (a == 0) {
                out[i] = p;
                continue;
            }
            Color.RGBtoHSB((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, hsb);
            hsb[1] = Math.min(1.0f, hsb[1] * factor);
            out[i] = (a << 24) | (Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0x00FFFFFF);
        }
        return fromPixels(out, w, h);
    }

    /** 투명한 바깥 여백을 잘라냅니다. 알파가 전혀 없으면 원본을 그대로 돌려줍니다. */
    public static BufferedImage trimTransparent(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);

        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                if (((px[row + x] >>> 24) & 0xFF) > TRIM_THRESHOLD) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) {
            return src;   // 전부 투명
        }
        if (minX == 0 && minY == 0 && maxX == w - 1 && maxY == h - 1) {
            return src;   // 자를 여백 없음 (배경 제거 안 된 일반 사진)
        }
        return src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /**
     * 띠부씰 느낌의 흰색 아웃라인을 두릅니다.
     *
     * <p>실루엣을 원 둘레를 따라 여러 번 겹쳐 그리는 방식입니다.
     * 픽셀 단위 팽창(dilation) 루프보다 훨씬 빠르고 가장자리가 매끄럽습니다.
     */
    public static BufferedImage addWhiteOutline(BufferedImage src, int width) {
        if (width <= 0) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();

        BufferedImage silhouette = whiteSilhouette(src);
        BufferedImage out = new BufferedImage(w + width * 2, h + width * 2, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = out.createGraphics();
        applyQualityHints(g);

        // 반지름을 두 단계로 나눠 그려서 얇은 부분에 구멍이 생기지 않게 합니다.
        int[] radii = {width, Math.max(1, width / 2)};
        for (int r : radii) {
            int steps = Math.max(24, (int) Math.ceil(2 * Math.PI * r));
            for (int i = 0; i < steps; i++) {
                double a = 2 * Math.PI * i / steps;
                int dx = (int) Math.round(r * Math.cos(a));
                int dy = (int) Math.round(r * Math.sin(a));
                g.drawImage(silhouette, width + dx, width + dy, null);
            }
        }
        g.drawImage(src, width, width, null);
        g.dispose();
        return out;
    }

    /** 정사각 캔버스 중앙에 배치합니다. 도감 격자에서 크기가 들쭉날쭉하지 않게 하는 단계입니다. */
    public static BufferedImage fitToSquare(BufferedImage src, int size, int padding) {
        int inner = Math.max(1, size - padding * 2);
        BufferedImage fitted = resizeToFit(src, inner);

        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        applyQualityHints(g);
        g.drawImage(fitted, (size - fitted.getWidth()) / 2, (size - fitted.getHeight()) / 2, null);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------
    // 내부 헬퍼
    // ------------------------------------------------------------------

    /** 알파 모양은 그대로 두고 색만 흰색으로 바꾼 실루엣. */
    private static BufferedImage whiteSilhouette(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.setComposite(AlphaComposite.SrcAtop);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.dispose();
        return out;
    }

    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage fromPixels(int[] px, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    private static void applyQualityHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private static int[] gainLut(double gain) {
        int[] lut = new int[256];
        for (int i = 0; i < 256; i++) {
            lut[i] = clamp((int) Math.round(i * gain));
        }
        return lut;
    }

    private static double clampGain(double g) {
        return clamp(g, 0.6, 1.8);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int luminance(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
