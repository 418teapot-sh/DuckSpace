package com.duckspace.domain.post.service;

import com.duckspace.domain.post.dto.request.CommentRequest;
import com.duckspace.domain.post.dto.response.CommentResponse;
import com.duckspace.domain.post.entity.Comment;
import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.post.repository.CommentRepository;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostService postService;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, userRepository, postService);
    }

    private Post post(Long id, Long ownerId) {
        Post post = Post.createCasual(ownerId, "본문");
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private Comment comment(Long id, Post post, Long userId, Comment parent, String content, boolean secret) {
        Comment comment = new Comment(post, userId, parent, content, secret);
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }

    @Nested
    @DisplayName("create 메서드는")
    class Create {

        @Test
        void 최상위_댓글을_저장한다() {
            Post post = post(1L, 100L);
            given(postService.getPost(1L)).willReturn(post);
            given(commentRepository.save(any(Comment.class))).willAnswer(invocation -> {
                Comment saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", 5L);
                return saved;
            });

            Long commentId = commentService.create(10L, 1L, new CommentRequest("댓글", null, false));

            assertThat(commentId).isEqualTo(5L);
        }

        @Test
        void 답글에는_답글을_달_수_없다() {
            Post post = post(1L, 100L);
            Comment topLevel = comment(5L, post, 10L, null, "댓글", false);
            Comment reply = comment(6L, post, 11L, topLevel, "답글", false);
            given(postService.getPost(1L)).willReturn(post);
            given(commentRepository.findById(6L)).willReturn(Optional.of(reply));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commentService.create(12L, 1L, new CommentRequest("대댓글", 6L, false)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.REPLY_TO_REPLY_NOT_ALLOWED);
        }

        @Test
        void 다른_게시글의_댓글에는_답글을_달_수_없다() {
            Post post = post(1L, 100L);
            Post otherPost = post(2L, 100L);
            Comment parentOnOtherPost = comment(5L, otherPost, 10L, null, "댓글", false);
            given(postService.getPost(1L)).willReturn(post);
            given(commentRepository.findById(5L)).willReturn(Optional.of(parentOnOtherPost));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commentService.create(12L, 1L, new CommentRequest("답글", 5L, false)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.PARENT_COMMENT_ON_DIFFERENT_POST);
        }
    }

    @Nested
    @DisplayName("list 메서드는")
    class ListComments {

        @Test
        void 존재하지_않는_게시글이면_예외() {
            given(postService.getPost(1L)).willThrow(new BusinessException(PostErrorCode.POST_NOT_FOUND));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commentService.list(1L, 999L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND);
        }

        @Test
        void 비밀댓글은_작성자와_게시글_주인만_내용을_볼_수_있다() {
            Post post = post(1L, 100L);
            Comment secret = comment(5L, post, 20L, null, "비밀 내용", true);
            given(commentRepository.findByPost_IdOrderByIdAsc(1L)).willReturn(List.of(secret));

            List<CommentResponse> asStranger = commentService.list(1L, 999L);
            List<CommentResponse> asAuthor = commentService.list(1L, 20L);
            List<CommentResponse> asPostOwner = commentService.list(1L, 100L);

            assertThat(asStranger.get(0).content()).isEqualTo("비밀 댓글입니다.");
            assertThat(asAuthor.get(0).content()).isEqualTo("비밀 내용");
            assertThat(asPostOwner.get(0).content()).isEqualTo("비밀 내용");
        }

        @Test
        void 답글은_부모_댓글_아래에_중첩되어_내려온다() {
            Post post = post(1L, 100L);
            Comment parent = comment(5L, post, 10L, null, "부모", false);
            Comment reply = comment(6L, post, 11L, parent, "답글", false);
            given(commentRepository.findByPost_IdOrderByIdAsc(1L)).willReturn(List.of(parent, reply));

            List<CommentResponse> responses = commentService.list(1L, 999L);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).replies()).hasSize(1);
            assertThat(responses.get(0).replies().get(0).content()).isEqualTo("답글");
        }
    }

    @Nested
    @DisplayName("delete 메서드는")
    class Delete {

        @Test
        void 타인_댓글이면_예외() {
            Post post = post(1L, 100L);
            Comment comment = comment(5L, post, 10L, null, "댓글", false);
            given(commentRepository.findById(5L)).willReturn(Optional.of(comment));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commentService.delete(5L, 999L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.NOT_COMMENT_OWNER);
        }

        @Test
        void 본인_댓글이면_삭제한다() {
            // 답글 cascade 삭제는 Comment.parent의 @OnDelete(CASCADE)로 DB가 처리합니다 —
            // 목(mock) 기반 단위 테스트로는 검증할 수 없고, 스키마/통합 테스트 영역입니다.
            Post post = post(1L, 100L);
            Comment comment = comment(5L, post, 10L, null, "댓글", false);
            given(commentRepository.findById(5L)).willReturn(Optional.of(comment));

            commentService.delete(5L, 10L);

            verify(commentRepository).delete(comment);
        }
    }
}
