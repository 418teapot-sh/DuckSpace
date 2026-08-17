package com.duckspace.domain.post.service;

import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.post.entity.PendingPostImage;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.post.repository.PendingPostImageRepository;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostImageServiceTest {

    private static final Long USER_ID = 10L;

    @Mock
    private ImageStorage imageStorage;

    @Mock
    private PendingPostImageRepository pendingPostImageRepository;

    private PostImageService postImageService;

    @BeforeEach
    void setUp() {
        postImageService = new PostImageService(imageStorage, pendingPostImageRepository);
    }

    private MockMultipartFile png(byte[] bytes) {
        return new MockMultipartFile("image", "goods.png", "image/png", bytes);
    }

    /** ExhibitionItemServiceTest와 같은 이유로, 헤더뿐 아니라 실제 바이트까지 진짜 PNG여야 통과합니다. */
    private byte[] realPngBytes() throws Exception {
        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void 진짜_이미지면_업로드하고_URL을_돌려준다() throws Exception {
        given(imageStorage.upload(anyString(), any(byte[].class), anyString()))
                .willReturn("https://cdn/posts/10/abc.png");

        String imageUrl = postImageService.upload(USER_ID, png(realPngBytes()));

        assertThat(imageUrl).isEqualTo("https://cdn/posts/10/abc.png");
    }

    @Test
    void 업로드한_이미지를_대기중으로_기록한다() throws Exception {
        // 이 기록이 없으면 PendingPostImageCleaner가 방금 올린 이미지도 그냥 지나칩니다(안전한 방향).
        // 반대로, 실제로 글에 쓰이지 않은 이미지는 이 기록이 있어야 나중에 정리됩니다.
        given(imageStorage.upload(anyString(), any(byte[].class), anyString()))
                .willReturn("https://cdn/posts/10/abc.png");

        postImageService.upload(USER_ID, png(realPngBytes()));

        ArgumentCaptor<PendingPostImage> captor = ArgumentCaptor.forClass(PendingPostImage.class);
        verify(pendingPostImageRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getImageUrl()).isEqualTo("https://cdn/posts/10/abc.png");
    }

    @Test
    void 빈_파일이면_거부한다() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> postImageService.upload(USER_ID, png(new byte[0])));

        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.EMPTY_IMAGE);
        verify(imageStorage, never()).upload(anyString(), any(), anyString());
    }

    @Test
    void 지원하지_않는_형식이면_거부한다() {
        MockMultipartFile pdf = new MockMultipartFile("image", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

        BusinessException exception = assertThrows(BusinessException.class,
                () -> postImageService.upload(USER_ID, pdf));

        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    void 이미지가_아닌_바이트는_헤더를_속여도_거부한다() {
        // Content-Type은 클라이언트가 보내는 값이라 믿을 수 없습니다.
        // 이걸 막지 않으면 업로드 창구가 그대로 파일 호스팅이 됩니다.
        MockMultipartFile disguised = new MockMultipartFile(
                "image", "goods.png", "image/png", "#!/bin/sh\necho hi".getBytes());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> postImageService.upload(USER_ID, disguised));

        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.UNSUPPORTED_IMAGE_TYPE);
        verify(imageStorage, never()).upload(anyString(), any(), anyString());
        verify(pendingPostImageRepository, never()).save(any());
    }
}
