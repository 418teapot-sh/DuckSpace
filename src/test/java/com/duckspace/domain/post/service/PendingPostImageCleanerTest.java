package com.duckspace.domain.post.service;

import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.post.entity.PendingPostImage;
import com.duckspace.domain.post.repository.PendingPostImageRepository;
import org.junit.jupiter.api.BeforeEach;
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
    private ImageStorage imageStorage;

    private PendingPostImageCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new PendingPostImageCleaner(pendingPostImageRepository, imageStorage);
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

        cleaner.cleanupAbandoned();

        verify(imageStorage).deleteByUrl("https://cdn/a.png");
        verify(imageStorage).deleteByUrl("https://cdn/b.png");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PendingPostImage>> captor = ArgumentCaptor.forClass(List.class);
        verify(pendingPostImageRepository).deleteAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
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
