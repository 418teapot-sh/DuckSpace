package com.duckspace.domain.post.service;

import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.entity.PostLike;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.post.repository.PostLikeRepository;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private PostService postService;

    private LikeService likeService;

    @BeforeEach
    void setUp() {
        likeService = new LikeService(postLikeRepository, postService);
    }

    private Post post(Long id) {
        Post post = Post.createCasual(100L, "본문");
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    @Nested
    @DisplayName("like 메서드는")
    class Like {

        @Test
        void 정상적으로_등록한다() {
            given(postService.getPost(1L)).willReturn(post(1L));
            given(postLikeRepository.existsByPostIdAndUserId(1L, 10L)).willReturn(false);

            likeService.like(10L, 1L);

            verify(postLikeRepository).saveAndFlush(any(PostLike.class));
        }

        @Test
        void 이미_좋아요한_상태면_예외() {
            given(postService.getPost(1L)).willReturn(post(1L));
            given(postLikeRepository.existsByPostIdAndUserId(1L, 10L)).willReturn(true);

            BusinessException exception = assertThrows(BusinessException.class, () -> likeService.like(10L, 1L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.ALREADY_LIKED);
            verify(postLikeRepository, never()).saveAndFlush(any());
        }

        @Test
        void 동시_요청으로_유니크_제약에_걸리면_이미_좋아요_예외로_변환한다() {
            given(postService.getPost(1L)).willReturn(post(1L));
            given(postLikeRepository.existsByPostIdAndUserId(1L, 10L)).willReturn(false);
            given(postLikeRepository.saveAndFlush(any(PostLike.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate"));

            BusinessException exception = assertThrows(BusinessException.class, () -> likeService.like(10L, 1L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.ALREADY_LIKED);
        }
    }

    @Nested
    @DisplayName("unlike 메서드는")
    class Unlike {

        @Test
        void 존재하면_삭제한다() {
            PostLike like = new PostLike(1L, 10L);
            given(postLikeRepository.findByPostIdAndUserId(1L, 10L)).willReturn(Optional.of(like));

            likeService.unlike(10L, 1L);

            verify(postLikeRepository).delete(like);
        }

        @Test
        void 이미_취소된_상태여도_에러_없이_넘어간다() {
            given(postLikeRepository.findByPostIdAndUserId(1L, 10L)).willReturn(Optional.empty());

            likeService.unlike(10L, 1L);

            verify(postLikeRepository, never()).delete(any());
        }
    }
}
