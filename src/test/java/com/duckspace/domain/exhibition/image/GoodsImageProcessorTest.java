package com.duckspace.domain.exhibition.image;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 순수 Java 라 Spring 컨텍스트 없이 바로 돌아갑니다.
 *
 * <p>파라미터 값 자체(384px, 테두리 없음)는 실제 굿즈 사진으로 눈으로 검증했고,
 * 여기서는 <b>코드가 그 약속을 지키는지</b>를 확인합니다.
 */
class GoodsImageProcessorTest {

    /** 투명 배경 가운데에 불투명한 사각형이 있는 이미지. 배경 제거 결과를 흉내냅니다. */
    private BufferedImage cutout(int canvas, int subjectSize, Color color) {
        BufferedImage img = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        int offset = (canvas - subjectSize) / 2;
        g.fillRect(offset, offset, subjectSize, subjectSize);
        g.dispose();
        return img;
    }

    private boolean isTransparent(BufferedImage img, int x, int y) {
        return ((img.getRGB(x, y) >>> 24) & 0xFF) == 0;
    }

    @Test
    @DisplayName("출력은 요청한 정사각 크기다")
    void 출력_크기가_정사각으로_통일된다() {
        BufferedImage result = GoodsImageProcessor.process(
                cutout(600, 200, Color.RED), GoodsImageProcessor.Options.forExhibition(384));

        assertThat(result.getWidth()).isEqualTo(384);
        assertThat(result.getHeight()).isEqualTo(384);
    }

    @Test
    @DisplayName("전시 프리셋은 흰 테두리를 넣지 않는다")
    void 전시_프리셋은_테두리가_없다() {
        BufferedImage subject = cutout(600, 200, Color.RED);

        BufferedImage noOutline = GoodsImageProcessor.process(
                subject, GoodsImageProcessor.Options.forExhibition(384));
        BufferedImage withOutline = GoodsImageProcessor.process(
                subject, GoodsImageProcessor.Options.forExhibition(384).withOutlineWidth(12));

        // 테두리를 두르면 피사체 주변에 불투명한 흰 픽셀이 생겨 여백이 줄어듭니다.
        int transparentNoOutline = countTransparent(noOutline);
        int transparentWithOutline = countTransparent(withOutline);

        assertThat(transparentNoOutline)
                .as("테두리가 없으면 투명 영역이 더 넓어야 합니다")
                .isGreaterThan(transparentWithOutline);
    }

    @Test
    @DisplayName("배경 투명도는 그대로 유지된다")
    void 투명_배경이_유지된다() {
        BufferedImage result = GoodsImageProcessor.process(
                cutout(600, 200, Color.RED), GoodsImageProcessor.Options.forExhibition(384));

        assertThat(isTransparent(result, 2, 2))
                .as("장식장 배경 위에 얹으려면 모서리가 투명해야 합니다")
                .isTrue();
    }

    @Test
    @DisplayName("피사체 주변 투명 여백은 잘려 나간다")
    void 여백_크기가_결과에_영향을_주지_않는다() {
        // 같은 크기의 피사체를 서로 다른 여백 안에 두어도, 여백이 잘리므로 결과가 같아야 합니다.
        BufferedImage tight = GoodsImageProcessor.process(
                cutout(300, 200, Color.RED), GoodsImageProcessor.Options.forExhibition(384));
        BufferedImage loose = GoodsImageProcessor.process(
                cutout(600, 200, Color.RED), GoodsImageProcessor.Options.forExhibition(384));

        assertThat(384 * 384 - countTransparent(loose))
                .as("여백만 다를 뿐 같은 피사체이므로 결과가 같아야 합니다")
                .isEqualTo(384 * 384 - countTransparent(tight));
    }

    @Test
    @DisplayName("피사체가 캔버스보다 작으면 억지로 늘리지 않는다")
    void 업스케일하지_않는다() {
        // 화면에서 작게 찍힌 굿즈를 캔버스 크기까지 늘리면 뭉개집니다.
        // 크기 통일보다 선명도를 택한 결과이며, 촬영 가이드로 보완할 부분입니다.
        BufferedImage small = GoodsImageProcessor.process(
                cutout(300, 120, Color.RED), GoodsImageProcessor.Options.forExhibition(384));

        int opaque = 384 * 384 - countTransparent(small);

        assertThat(opaque)
                .as("120x120 피사체가 캔버스를 꽉 채우도록 늘어나면 안 됩니다")
                .isLessThan(200 * 200);
    }

    @Test
    @DisplayName("알파가 없는 일반 사진도 처리된다")
    void 알파없는_이미지도_처리된다() {
        BufferedImage jpegLike = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = jpegLike.createGraphics();
        g.setColor(new Color(90, 70, 40));
        g.fillRect(0, 0, 400, 300);
        g.dispose();

        BufferedImage result = GoodsImageProcessor.process(
                jpegLike, GoodsImageProcessor.Options.forExhibition(384));

        assertThat(result.getWidth()).isEqualTo(384);
        assertThat(result.getHeight()).isEqualTo(384);
    }

    @Test
    @DisplayName("큰 원본은 작업 크기로 먼저 줄여 메모리를 아낀다")
    void 큰_원본도_처리된다() {
        BufferedImage huge = cutout(3000, 1500, Color.BLUE);

        BufferedImage result = GoodsImageProcessor.process(
                huge, GoodsImageProcessor.Options.forExhibition(384));

        assertThat(result.getWidth()).isEqualTo(384);
    }

    private int countTransparent(BufferedImage img) {
        int count = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (isTransparent(img, x, y)) {
                    count++;
                }
            }
        }
        return count;
    }
}
