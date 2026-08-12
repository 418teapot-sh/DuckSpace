package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.exhibition.image.RemoveBgClient;
import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExhibitionImageProcessorTest {

    private static final Long ITEM_ID = 5L;

    @Mock
    private RemoveBgClient removeBgClient;

    @Mock
    private ImageStorage imageStorage;

    @Mock
    private ExhibitionItemRepository exhibitionItemRepository;

    @InjectMocks
    private ExhibitionImageProcessor processor;

    private ExhibitionItem item;
    private byte[] imageBytes;

    @BeforeEach
    void setUp() throws IOException {
        Exhibition exhibition = new Exhibition(1L, "장식장1", null);
        ReflectionTestUtils.setField(exhibition, "id", 10L);

        item = new ExhibitionItem(exhibition, new ExhibitionItem.Placement(0.2, 0.3, 0.2, 0.2),
                null, "굿즈", null, null, ItemStatus.PENDING);
        ReflectionTestUtils.setField(item, "id", ITEM_ID);

        imageBytes = pngBytes();
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(50, 50, 100, 100);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private void handle() {
        processor.handle(new ItemImageUploadedEvent(ITEM_ID, imageBytes, "goods.png"));
    }

    @Test
    @DisplayName("배경 제거에 성공하면 READY 로 바뀌고 URL 이 채워진다")
    void 성공하면_READY() throws Exception {
        given(exhibitionItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
        given(removeBgClient.isEnabled()).willReturn(true);
        given(removeBgClient.removeBackground(any(), anyString())).willReturn(ImageIO.read(new java.io.ByteArrayInputStream(imageBytes)));
        given(imageStorage.upload(anyString(), any(), eq("image/png"))).willReturn("https://cdn/result.png");

        handle();

        assertThat(item.getStatus()).isEqualTo(ItemStatus.READY);
        assertThat(item.getImageUrl()).isEqualTo("https://cdn/result.png");
    }

    @Test
    @DisplayName("API 키가 없으면 배경 제거를 건너뛰고 원본으로 처리한다")
    void 키가_없으면_원본으로_진행() {
        given(exhibitionItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
        given(removeBgClient.isEnabled()).willReturn(false);
        given(imageStorage.upload(anyString(), any(), eq("image/png"))).willReturn("https://cdn/result.png");

        handle();

        assertThat(item.getStatus())
                .as("키가 없다고 업로드 자체가 실패하면 안 됩니다")
                .isEqualTo(ItemStatus.READY);
    }

    @Test
    @DisplayName("배경 제거가 실패해도 원본으로 계속 진행한다")
    void 배경제거_실패해도_READY() throws Exception {
        given(exhibitionItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
        given(removeBgClient.isEnabled()).willReturn(true);
        given(removeBgClient.removeBackground(any(), anyString()))
                .willThrow(new IOException("remove.bg 402"));
        given(imageStorage.upload(anyString(), any(), eq("image/png"))).willReturn("https://cdn/result.png");

        handle();

        assertThat(item.getStatus())
                .as("외부 API 장애로 업로드가 통째로 실패하면 안 됩니다")
                .isEqualTo(ItemStatus.READY);
    }

    @Test
    @DisplayName("저장에 실패하면 FAILED 로 남기되 원본은 살려둔다")
    void 저장_실패하면_FAILED() {
        given(exhibitionItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
        given(removeBgClient.isEnabled()).willReturn(false);
        given(imageStorage.upload(anyString(), any(), eq("image/png")))
                .willThrow(new RuntimeException("S3 down"))
                .willReturn("https://cdn/original.png");

        handle();

        assertThat(item.getStatus()).isEqualTo(ItemStatus.FAILED);
        assertThat(item.getImageUrl())
                .as("사용자가 올린 사진이 사라지면 안 됩니다")
                .isEqualTo("https://cdn/original.png");
    }

    @Test
    @DisplayName("처리 중 아이템이 삭제되었으면 조용히 끝낸다")
    void 아이템이_없으면_아무것도_하지_않는다() {
        given(exhibitionItemRepository.findById(ITEM_ID)).willReturn(Optional.empty());

        handle();

        verify(imageStorage, never()).upload(anyString(), any(), anyString());
    }
}
