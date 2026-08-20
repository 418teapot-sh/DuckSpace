package com.duckspace.domain.post.service;

import com.duckspace.domain.exhibition.image.ImageCleanup;
import com.duckspace.domain.post.entity.PendingPostImage;
import com.duckspace.domain.post.repository.PendingPostImageRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PendingPostImageCleanerTest {

    @Mock
    private PendingPostImageRepository pendingPostImageRepository;

    @Mock
    private ImageCleanup imageCleanup;

    private PendingPostImageCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new PendingPostImageCleaner(pendingPostImageRepository, imageCleanup);
    }

    private PendingPostImage pending(Long id, String imageUrl) {
        PendingPostImage image = new PendingPostImage(1L, imageUrl);
        ReflectionTestUtils.setField(image, "id", id);
        return image;
    }

    @Test
    @DisplayName("방치된 이미지는 행을 지우고, 파일 삭제는 ImageCleanup 에 맡긴다")
    void 오래_방치된_이미지를_저장소와_함께_지운다() {
        given(pendingPostImageRepository.findByCreatedAtBefore(any(), any(Pageable.class)))
                .willReturn(List.of(pending(1L, "https://cdn/a.png"), pending(2L, "https://cdn/b.png")));

        cleaner.cleanupAbandoned();

        // 파일 삭제는 ImageCleanup 이 맡습니다 — 참조 확인·재시도·전용 실행기가 거기 있습니다.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> urls = ArgumentCaptor.forClass(List.class);
        verify(imageCleanup).deleteAfterCommit(urls.capture());
        assertThat(urls.getValue()).containsExactly("https://cdn/a.png", "https://cdn/b.png");

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

        verify(imageCleanup, never()).deleteAfterCommit(anyList());
        verify(pendingPostImageRepository, never()).deleteAll(any());
    }
}
