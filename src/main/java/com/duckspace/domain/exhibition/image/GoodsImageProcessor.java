package com.duckspace.domain.exhibition.image;

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
 *   3) 채도 부스트           화면에서 작게 보일 때 또렷하도록
 *   4) 투명 여백 트림        피사체에 딱 맞게 크롭
 *   5) 정사각 캔버스 배치     굿즈끼리 크기 기준 통일
 * </pre>
 *
 * <p>2~3번은 알파가 없는 일반 JPG 에도 동작합니다.
 * 4번은 <b>배경이 제거되어 알파 채널이 있는 이미지</b>여야 의미가 있습니다.
 *
 * <h2>버린 선택지 (다시 시도하기 전에 읽어주세요)</h2>
 *
 * <p>실제 굿즈 사진(0.25MP remove.bg 출력)으로 비교해보고 <b>넣지 않기로 한 것들</b>입니다.
 * 눈으로 확인한 결과라 코드만 보고는 알 수 없어서 남겨둡니다.
 *
 * <ul>
 *   <li><b>자동 레벨(히스토그램 풀 스트레치)</b> — 어두운 사진에는 좋지만 크림색·흰색 계열 굿즈의
 *       가장 밝은 부분이 255 로 밀려 <b>하얗게 날아갑니다.</b> 그래서 감마로 평균 밝기만 맞추는
 *       방식({@link #normalizeBrightness})을 씁니다. 0 과 255 를 건드리지 않아 하이라이트가 삽니다.
 *   <li><b>그레이월드 화이트밸런스</b> — "화면 전체 평균이 회색" 가정은 배경을 오려낸 단색 굿즈에
 *       맞지 않습니다. 갈색 키링이 올리브색이 되는 식으로 <b>피사체 고유색을 파괴</b>했습니다.
 *   <li><b>화이트패치 화이트밸런스</b> — 그레이월드보다는 낫지만, 크림색 인형을 순백으로
 *       바꿔버렸습니다. 조명색과 굿즈색을 구분하지 못합니다.
 *   <li><b>흰색 아웃라인</b> — 띠부씰 도감에는 어울리지만, 실제 책장·협탁 배경 위에 얹으면
 *       오려붙인 스티커처럼 보입니다.
 * </ul>
 *
 * <p>결론적으로 화이트밸런스는 <b>하지 않는 것</b>이 굿즈 사진에 가장 안전했습니다.
 */
public final class GoodsImageProcessor {

    /** 통계 계산 시 "피사체"로 간주할 최소 알파값. 반투명 가장자리를 제외합니다. */
    private static final int OPAQUE_THRESHOLD = 16;

    /** 트림 시 살릴 최소 알파값. */
    private static final int TRIM_THRESHOLD = 10;

    /** 감마 밝기 보정이 맞추려는 목표 평균 밝기. 너무 높이면 밝은 굿즈가 날아갑니다. */
    private static final double TARGET_MEAN_LUMINANCE = 165.0;

    /**
     * 처리 파라미터.
     *
     * @param maxWorkingSize  처리 전 리사이즈할 긴 변 (px). 메모리 보호용
     * @param outputSize      최종 정사각 한 변 (px)
     * @param padding         정사각 캔버스 안쪽 여백 (px)
     * @param saturationBoost 채도 배율. 1.0 = 원본
     */
    public record Options(
            int maxWorkingSize,
            int outputSize,
            int padding,
            float saturationBoost
    ) {
        /**
         * 전시(장식장)용 프리셋.
         *
         * <p>여백을 작게 잡습니다. 화면에서 작게 보이므로 피사체가 캔버스를 최대한 채워야 합니다.
         * 출력이 384px 을 넘으면 remove.bg 무료 출력(0.25MP)에서 업스케일이 시작됩니다.
         */
        public static Options forExhibition(int outputSize) {
            return new Options(1024, outputSize, 8, 1.10f);
        }
    }

    private GoodsImageProcessor() {
    }

    /**
     * 전체 파이프라인을 실행합니다.
     *
     * <p>단계 순서와 각 단계의 근거는 클래스 주석을 참고하세요.
     */
    public static BufferedImage process(BufferedImage src, Options o) {
        BufferedImage img = resizeToFit(toArgb(src), o.maxWorkingSize());

        img = normalizeBrightness(img);
        if (o.saturationBoost() != 1.0f) {
            img = adjustSaturation(img, o.saturationBoost());
        }
        img = trimTransparent(img);

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
     * 감마 곡선으로 평균 밝기를 {@value #TARGET_MEAN_LUMINANCE} 근처로 맞춥니다.
     *
     * <p>0 과 255 는 그대로 두므로 <b>밝은 부분이 타지 않습니다.</b> 히스토그램을 끝까지 늘리는
     * 방식을 쓰지 않는 이유는 클래스 주석의 "버린 선택지"를 참고하세요.
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
        double gamma = clamp(Math.log(TARGET_MEAN_LUMINANCE / 255.0) / Math.log(mean / 255.0), 0.65, 1.5);
        if (Math.abs(gamma - 1.0) < 0.02) {
            return src;
        }

        int[] lut = new int[256];
        for (int i = 0; i < 256; i++) {
            lut[i] = clamp((int) Math.round(255.0 * Math.pow(i / 255.0, gamma)));
        }
        return applyLut(px, w, h, lut);
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

    /** 정사각 캔버스 중앙에 배치합니다. 굿즈끼리 크기가 들쭉날쭉하지 않게 하는 단계입니다. */
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

    /** 알파는 그대로 두고 RGB 세 채널에 같은 변환표를 적용합니다. */
    private static BufferedImage applyLut(int[] px, int w, int h, int[] lut) {
        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            out[i] = (((p >>> 24) & 0xFF) << 24)
                    | (lut[(p >> 16) & 0xFF] << 16)
                    | (lut[(p >> 8) & 0xFF] << 8)
                    | lut[p & 0xFF];
        }
        return fromPixels(out, w, h);
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
