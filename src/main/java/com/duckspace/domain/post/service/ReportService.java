package com.duckspace.domain.post.service;

import com.duckspace.domain.post.dto.request.ReportRequest;
import com.duckspace.domain.post.entity.Comment;
import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.entity.Report;
import com.duckspace.domain.post.entity.ReportTargetType;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.post.repository.CommentRepository;
import com.duckspace.domain.post.repository.ReportRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final CommentRepository commentRepository;
    private final PostService postService;

    @Transactional
    public void reportPost(Long reporterId, Long postId, ReportRequest request) {
        Post post = postService.getPost(postId);
        if (post.isOwnedBy(reporterId)) {
            throw new BusinessException(PostErrorCode.CANNOT_REPORT_OWN_CONTENT);
        }

        reportRepository.save(new Report(reporterId, ReportTargetType.POST, postId, request.reason()));
    }

    @Transactional
    public void reportComment(Long reporterId, Long commentId, ReportRequest request) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(PostErrorCode.COMMENT_NOT_FOUND));
        if (comment.isOwnedBy(reporterId)) {
            throw new BusinessException(PostErrorCode.CANNOT_REPORT_OWN_CONTENT);
        }

        reportRepository.save(new Report(reporterId, ReportTargetType.COMMENT, commentId, request.reason()));
    }
}
