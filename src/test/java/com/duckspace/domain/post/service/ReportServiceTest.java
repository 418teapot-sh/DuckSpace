package com.duckspace.domain.post.service;

import com.duckspace.domain.post.dto.request.ReportRequest;
import com.duckspace.domain.post.entity.Comment;
import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.entity.ReportTargetType;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.post.repository.CommentRepository;
import com.duckspace.domain.post.repository.ReportRepository;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostService postService;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, commentRepository, postService);
    }

    private Post post(Long id, Long ownerId) {
        Post post = Post.createCasual(ownerId, "본문");
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private Comment comment(Long id, Post post, Long userId) {
        Comment comment = new Comment(post, userId, null, "댓글", false);
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }

    @Nested
    @DisplayName("reportPost 메서드는")
    class ReportPost {

        @Test
        void 정상적으로_신고를_저장한다() {
            given(postService.getPost(1L)).willReturn(post(1L, 100L));

            reportService.reportPost(999L, 1L, new ReportRequest("스팸"));

            verify(reportRepository).save(any());
        }

        @Test
        void 본인_글이면_예외() {
            given(postService.getPost(1L)).willReturn(post(1L, 100L));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> reportService.reportPost(100L, 1L, new ReportRequest(null)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.CANNOT_REPORT_OWN_CONTENT);
            verify(reportRepository, never()).save(any());
        }

        @Test
        void 이미_신고한_글이면_예외() {
            given(postService.getPost(1L)).willReturn(post(1L, 100L));
            given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(999L, ReportTargetType.POST, 1L))
                    .willReturn(true);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> reportService.reportPost(999L, 1L, new ReportRequest(null)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.ALREADY_REPORTED);
            verify(reportRepository, never()).save(any());
        }

        @Test
        void 본문_없이_신고해도_저장된다() {
            given(postService.getPost(1L)).willReturn(post(1L, 100L));

            reportService.reportPost(999L, 1L, null);

            verify(reportRepository).save(any());
        }
    }

    @Nested
    @DisplayName("reportComment 메서드는")
    class ReportComment {

        @Test
        void 정상적으로_신고를_저장한다() {
            Comment comment = comment(5L, post(1L, 100L), 10L);
            given(commentRepository.findById(5L)).willReturn(Optional.of(comment));

            reportService.reportComment(999L, 5L, new ReportRequest(null));

            verify(reportRepository).save(any());
        }

        @Test
        void 본인_댓글이면_예외() {
            Comment comment = comment(5L, post(1L, 100L), 10L);
            given(commentRepository.findById(5L)).willReturn(Optional.of(comment));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> reportService.reportComment(10L, 5L, new ReportRequest(null)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.CANNOT_REPORT_OWN_CONTENT);
        }
    }
}
