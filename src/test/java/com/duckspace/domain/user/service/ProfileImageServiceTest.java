package com.duckspace.domain.user.service;

import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.user.exception.UserErrorCode;
import com.duckspace.domain.user.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProfileImageServiceTest {

    private static final Long USER_ID = 10L;

    @Mock
    private ImageStorage imageStorage;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileImageWriter profileImageWriter;

    private ProfileImageService profileImageService;

    @BeforeEach
    void setUp() {
        profileImageService = new ProfileImageService(imageStorage, userRepository, profileImageWriter);
    }

    private MockMultipartFile png(byte[] bytes) {
        return new MockMultipartFile("image", "profile.png", "image/png", bytes);
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
        given(userRepository.existsById(USER_ID)).willReturn(true);
        given(imageStorage.upload(anyString(), any(byte[].class), anyString()))
                .willReturn("https://cdn/users/10/abc.png");

        String imageUrl = profileImageService.upload(USER_ID, png(realPngBytes()));

        assertThat(imageUrl).isEqualTo("https://cdn/users/10/abc.png");
        verify(profileImageWriter).apply(USER_ID, "https://cdn/users/10/abc.png");
    }

    @Test
    void 실제_포맷으로_ContentType을_저장한다() throws Exception {
        // 클라이언트가 헤더를 자칭해도(여기선 우연히 맞지만) 실제로 넘기는 값은
        // 바이트를 디코딩해서 판별한 포맷이어야 합니다. S3 Content-Type 메타데이터가
        // 어긋나면 브라우저가 잘못 렌더링합니다.
        given(userRepository.existsById(USER_ID)).willReturn(true);
        given(imageStorage.upload(anyString(), any(byte[].class), anyString()))
                .willReturn("https://cdn/users/10/abc.png");

        profileImageService.upload(USER_ID, png(realPngBytes()));

        verify(imageStorage).upload(anyString(), any(byte[].class), eq("image/png"));
    }

    @Test
    void 존재하지_않는_유저면_거부하고_업로드하지_않는다() throws Exception {
        given(userRepository.existsById(USER_ID)).willReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> profileImageService.upload(USER_ID, png(realPngBytes())));

        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        verify(imageStorage, never()).upload(anyString(), any(), anyString());
    }

    @Test
    void 빈_파일이면_거부한다() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> profileImageService.upload(USER_ID, png(new byte[0])));

        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.EMPTY_IMAGE);
        verify(imageStorage, never()).upload(anyString(), any(), anyString());
    }

    @Test
    void 지원하지_않는_형식이면_거부한다() {
        MockMultipartFile pdf = new MockMultipartFile("image", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

        BusinessException exception = assertThrows(BusinessException.class,
                () -> profileImageService.upload(USER_ID, pdf));

        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    void 이미지가_아닌_바이트는_헤더를_속여도_거부한다() {
        // Content-Type은 클라이언트가 보내는 값이라 믿을 수 없습니다.
        MockMultipartFile disguised = new MockMultipartFile(
                "image", "profile.png", "image/png", "#!/bin/sh\necho hi".getBytes());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> profileImageService.upload(USER_ID, disguised));

        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.UNSUPPORTED_IMAGE_TYPE);
        verify(imageStorage, never()).upload(anyString(), any(), anyString());
        verify(profileImageWriter, never()).apply(any(), any());
    }
}
