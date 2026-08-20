package com.duckspace.domain.post.service;

import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.post.entity.PendingPostImage;
import com.duckspace.domain.post.repository.PendingPostImageRepository;
import com.duckspace.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PendingPostImageCleanerTest {

    @Mock
    private PendingPostImageRepository pendingPostImageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ImageStorage imageStorage;

    private PendingPostImageCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new PendingPostImageCleaner(pendingPostImageRepository, userRepository, imageStorage);
    }

    private PendingPostImage pending(Long id, String imageUrl) {
        PendingPostImage image = new PendingPostImage(1L, imageUrl);
        ReflectionTestUtils.setField(image, "id", id);
        return image;
    }

    @Test
    void 오래_방치된_이미지를_저장소와_함께_지운다() {
        given(pendingPostImageRepository.findByCreatedAtBefore(any(), any(Pageable.class)))
                .willReturn(List.of(pending(1L, "https://cdn/a.png"), pending(2L, "https://cdn/b.png")));
        given(userRepository.findProfileImageUrlsIn(any())).willReturn(List.of());

        cleaner.cleanupAbandoned();

        verify(imageStorage).deleteByUrl("https://cdn/a.png");
        verify(imageStorage).deleteByUrl("https://cdn/b.png");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PendingPostImage>> captor = ArgumentCaptor.forClass(List.class);
        verify(pendingPostImageRepository).deleteAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("프로필 사진으로 쓰이는 이미지는 파일을 안 지운다")
    void 프로필로_쓰이면_파일을_남긴다() {
        // 프론트가 프로필 사진을 게시글 이미지 업로드로 올려서, 그대로 두면 "글에 안 쓰인
        // 이미지" 로 잡혀 24시간 뒤 파일이 사라집니다. users.profile_image_url 은 남고
        // 파일만 없어져 깨진 아바타가 됩니다.
        given(pendingPostImageRepository.findByCreatedAtBefore(any(), any(Pageable.class)))
                .willReturn(List.of(
                        pending(1L, "https://cdn/profile.png"),
                        pending(2L, "https://cdn/orphan.png")));
        given(userRepository.findProfileImageUrlsIn(any()))
                .willReturn(List.of("https://cdn/profile.png"));

        cleaner.cleanupAbandoned();

        verify(imageStorage, never()).deleteByUrl("https://cdn/profile.png");
        verify(imageStorage).deleteByUrl("https://cdn/orphan.png");
    }

    @Test
    @DisplayName("파일을 남기더라도 표시(행)는 지운다 — 배치가 막히지 않도록")
    void 프로필로_쓰여도_마커는_지운다() {
        // 마커의 뜻이 "아직 아무 데도 안 쓰임" 인데 이미 쓰이고 있으니 남길 이유가 없습니다.
        // 남기면 매시간 같은 행을 다시 집어오고, BATCH_SIZE(200)를 그런 행이 채우면
        // 진짜 고아 이미지가 영영 안 지워집니다.
        given(pendingPostImageRepository.findByCreatedAtBefore(any(), any(Pageable.class)))
                .willReturn(List.of(pending(1L, "https://cdn/profile.png")));
        given(userRepository.findProfileImageUrlsIn(any()))
                .willReturn(List.of("https://cdn/profile.png"));

        cleaner.cleanupAbandoned();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PendingPostImage>> captor = ArgumentCaptor.forClass(List.class);
        verify(pendingPostImageRepository).deleteAll(captor.capture());
        assertThat(captor.getValue())
                .as("파일은 남겨도 행은 지워야 합니다")
                .hasSize(1);
    }

    @Test
    void 정리_대상이_없으면_아무것도_지우지_않는다() {
        given(pendingPostImageRepository.findByCreatedAtBefore(any(), any(Pageable.class)))
                .willReturn(List.of());

        cleaner.cleanupAbandoned();

        verify(imageStorage, never()).deleteByUrl(any());
        verify(pendingPostImageRepository, never()).deleteAll(any());
    }
}
