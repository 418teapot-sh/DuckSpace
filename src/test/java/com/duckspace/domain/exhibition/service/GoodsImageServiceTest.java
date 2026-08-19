package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.dto.response.GoodsImagePageResponse;
import com.duckspace.domain.exhibition.dto.response.GoodsImageResponse;
import com.duckspace.domain.exhibition.entity.GoodsImage;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.domain.exhibition.image.ImageCleanup;
import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import com.duckspace.domain.exhibition.repository.GoodsImageRepository;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GoodsImageServiceTest {

    private static final Long ME = 1L;
    private static final Long STRANGER = 99L;
    private static final Long IMAGE_ID = 7L;

    @Mock
    private GoodsImageRepository goodsImageRepository;

    @Mock
    private ExhibitionItemRepository exhibitionItemRepository;

    @Mock
    private ImageCleanup imageCleanup;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GoodsImageService goodsImageService;

    private GoodsImage image(ItemStatus status, String imageUrl) {
        GoodsImage image = new GoodsImage(ME);
        ReflectionTestUtils.setField(image, "id", IMAGE_ID);
        ReflectionTestUtils.setField(image, "status", status);
        ReflectionTestUtils.setField(image, "imageUrl", imageUrl);
        return image;
    }

    private static byte[] realPngBytes() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    @Nested
    @DisplayName("upload 메서드는")
    class Upload {

        @Test
        void 접수만_하고_PENDING_으로_응답한다() throws Exception {
            given(goodsImageRepository.save(any(GoodsImage.class))).willAnswer(inv -> {
                GoodsImage saved = inv.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", IMAGE_ID);
                return saved;
            });

            GoodsImageResponse response = goodsImageService.upload(
                    ME, new MockMultipartFile("image", "goods.png", "image/png", realPngBytes()));

            assertThat(response.imageId()).isEqualTo(IMAGE_ID);
            assertThat(response.status()).isEqualTo(ItemStatus.PENDING);
            assertThat(response.imageUrl()).isNull();

            ArgumentCaptor<GoodsImageUploadedEvent> captor =
                    ArgumentCaptor.forClass(GoodsImageUploadedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().userId())
                    .as("저장 경로(images/{userId})를 만드는 데 필요합니다")
                    .isEqualTo(ME);
        }

        @Test
        void 이미지가_아닌_바이트는_헤더를_속여도_거부한다() {
            // 굿즈 업로드와 같은 검증을 씁니다 — 위장 파일이 보관함에 쌓이면 안 됩니다.
            var disguised = new MockMultipartFile(
                    "image", "goods.png", "image/png", "#!/bin/sh".getBytes());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> goodsImageService.upload(ME, disguised));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.UNSUPPORTED_IMAGE_TYPE);
            verify(goodsImageRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("get / list 메서드는")
    class Read {

        @Test
        void 남의_사진은_존재도_숨긴다() {
            given(goodsImageRepository.findById(IMAGE_ID))
                    .willReturn(Optional.of(image(ItemStatus.READY, "https://cdn/a.png")));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> goodsImageService.get(IMAGE_ID, STRANGER));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.IMAGE_NOT_FOUND);
        }

        @Test
        void 다음_페이지가_있으면_커서를_준다() {
            GoodsImage a = image(ItemStatus.READY, "a");
            GoodsImage b = image(ItemStatus.READY, "b");
            GoodsImage c = image(ItemStatus.READY, "c");
            ReflectionTestUtils.setField(a, "id", 30L);
            ReflectionTestUtils.setField(b, "id", 20L);
            ReflectionTestUtils.setField(c, "id", 10L);
            given(goodsImageRepository.findByUserIdOrderByIdDesc(eq(ME), any(Pageable.class)))
                    .willReturn(new java.util.ArrayList<>(List.of(a, b, c)));

            GoodsImagePageResponse page = goodsImageService.list(ME, null, 2);

            assertThat(page.images()).extracting(GoodsImageResponse::imageId).containsExactly(30L, 20L);
            assertThat(page.hasNext()).isTrue();
            assertThat(page.nextCursor()).isEqualTo(20L);
        }
    }

    @Nested
    @DisplayName("retry 메서드는")
    class Retry {

        @Test
        void 실패한_사진은_원본을_다시_태운다() {
            given(goodsImageRepository.findOwnedForUpdate(IMAGE_ID, ME))
                    .willReturn(Optional.of(image(ItemStatus.FAILED, "https://cdn/origin.png")));

            GoodsImageResponse response = goodsImageService.retry(IMAGE_ID, ME);

            assertThat(response.status()).isEqualTo(ItemStatus.PENDING);
            ArgumentCaptor<GoodsImageRetryRequestedEvent> captor =
                    ArgumentCaptor.forClass(GoodsImageRetryRequestedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().sourceImageUrl()).isEqualTo("https://cdn/origin.png");
        }

        @Test
        void 갓_접수된_PENDING은_거부하고_방치된_PENDING은_허용한다() {
            GoodsImage fresh = image(ItemStatus.PENDING, "https://cdn/origin.png");
            ReflectionTestUtils.setField(fresh, "updatedAt", LocalDateTime.now());
            given(goodsImageRepository.findOwnedForUpdate(IMAGE_ID, ME)).willReturn(Optional.of(fresh));

            assertThat(assertThrows(BusinessException.class,
                    () -> goodsImageService.retry(IMAGE_ID, ME)).getErrorCode())
                    .isEqualTo(ExhibitionErrorCode.IMAGE_NOT_RETRYABLE);

            // 강제 종료로 20분째 방치된 PENDING 은 실패로 간주하고 열어줍니다.
            ReflectionTestUtils.setField(fresh, "updatedAt", LocalDateTime.now().minusMinutes(20));
            assertThat(goodsImageService.retry(IMAGE_ID, ME).status()).isEqualTo(ItemStatus.PENDING);

            // PENDING→PENDING 은 바뀐 값이 없어 더티체킹으로는 UPDATE 가 안 나갑니다.
            // 방치 시계(updatedAt)를 명시적으로 되감지 않으면 연타마다 재처리가 중복 접수됩니다.
            verify(goodsImageRepository).touchUpdatedAt(org.mockito.ArgumentMatchers.eq(IMAGE_ID),
                    org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        }

        @Test
        void 원본이_없으면_다시_올리라고_알려준다() {
            given(goodsImageRepository.findOwnedForUpdate(IMAGE_ID, ME))
                    .willReturn(Optional.of(image(ItemStatus.FAILED, null)));

            assertThat(assertThrows(BusinessException.class,
                    () -> goodsImageService.retry(IMAGE_ID, ME)).getErrorCode())
                    .isEqualTo(ExhibitionErrorCode.RETRY_SOURCE_MISSING);
        }
    }

    @Nested
    @DisplayName("delete 메서드는")
    class Delete {

        @Test
        void 장식장에_배치된_사진은_삭제를_거부한다() {
            // 여기서 파일을 지우면 배치된 굿즈의 그림이 통째로 깨집니다.
            given(goodsImageRepository.findOwnedForUpdate(IMAGE_ID, ME))
                    .willReturn(Optional.of(image(ItemStatus.READY, "https://cdn/a.png")));
            given(exhibitionItemRepository.existsByImageUrl("https://cdn/a.png")).willReturn(true);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> goodsImageService.delete(IMAGE_ID, ME));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.IMAGE_IN_USE);
            verify(goodsImageRepository, never()).delete(any(GoodsImage.class));
        }

        @Test
        void 배치되지_않은_사진은_파일까지_지운다() {
            GoodsImage target = image(ItemStatus.READY, "https://cdn/a.png");
            given(goodsImageRepository.findOwnedForUpdate(IMAGE_ID, ME)).willReturn(Optional.of(target));
            given(exhibitionItemRepository.existsByImageUrl("https://cdn/a.png")).willReturn(false);

            goodsImageService.delete(IMAGE_ID, ME);

            verify(goodsImageRepository).delete(target);
            verify(imageCleanup).deleteAfterCommit("https://cdn/a.png");
        }

        @Test
        void 잠그고_읽는다() {
            // 잠그지 않으면 같은 사진에 삭제가 두 번 들어왔을 때 둘 다 통과하고,
            // 뒤에 온 쪽이 이미 없는 행을 지우려다 500 이 됩니다. retry() 와 같은 이유입니다.
            given(goodsImageRepository.findOwnedForUpdate(IMAGE_ID, ME))
                    .willReturn(Optional.of(image(ItemStatus.READY, "https://cdn/a.png")));

            goodsImageService.delete(IMAGE_ID, ME);

            verify(goodsImageRepository, never()).findById(any());
        }

        @Test
        void 처리_중_삭제는_행만_지운다() {
            // PENDING 이라 아직 파일이 없습니다. 처리가 뒤늦게 끝나도 상태 기록이
            // no-op 이 되면서 처리본은 파이프라인이 알아서 회수합니다.
            GoodsImage pending = image(ItemStatus.PENDING, null);
            given(goodsImageRepository.findOwnedForUpdate(IMAGE_ID, ME)).willReturn(Optional.of(pending));

            goodsImageService.delete(IMAGE_ID, ME);

            verify(goodsImageRepository).delete(pending);
            verify(imageCleanup).deleteAfterCommit((String) null);
        }
    }
}
