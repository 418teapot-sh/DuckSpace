package com.duckspace.domain.post.service;

import com.duckspace.domain.post.dto.request.CommentRequest;
import com.duckspace.domain.post.dto.response.CommentResponse;
import com.duckspace.domain.post.entity.Comment;
import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.post.repository.CommentRepository;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final PostService postService;

    @Transactional
    public Long create(Long userId, Long postId, CommentRequest request) {
        Post post = postService.getPost(postId);
        Comment parent = resolveParent(postId, request.parentId());

        Comment comment = commentRepository.save(
                new Comment(post, userId, parent, request.content(), request.secret()));
        return comment.getId();
    }

    public List<CommentResponse> list(Long postId, Long viewerId) {
        postService.getPost(postId);
        List<Comment> comments = commentRepository.findByPost_IdOrderByIdAsc(postId);
        if (comments.isEmpty()) {
            return List.of();
        }

        List<Long> authorIds = comments.stream().map(Comment::getUserId).distinct().toList();
        Map<Long, String> nicknames = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        Map<Long, List<Comment>> repliesByParentId = comments.stream()
                .filter(Comment::isReply)
                .collect(Collectors.groupingBy(comment -> comment.getParent().getId()));

        return comments.stream()
                .filter(comment -> !comment.isReply())
                .map(comment -> toResponse(comment, viewerId, nicknames, repliesByParentId))
                .toList();
    }

    @Transactional
    public void delete(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(PostErrorCode.COMMENT_NOT_FOUND));
        if (!comment.isOwnedBy(userId)) {
            throw new BusinessException(PostErrorCode.NOT_COMMENT_OWNER);
        }

        // 답글은 Comment.parent의 ON DELETE CASCADE로 DB가 같이 지웁니다.
        commentRepository.delete(comment);
    }

    private Comment resolveParent(Long postId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        Comment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException(PostErrorCode.PARENT_COMMENT_NOT_FOUND));
        if (!parent.getPost().getId().equals(postId)) {
            throw new BusinessException(PostErrorCode.PARENT_COMMENT_ON_DIFFERENT_POST);
        }
        if (parent.isReply()) {
            throw new BusinessException(PostErrorCode.REPLY_TO_REPLY_NOT_ALLOWED);
        }
        return parent;
    }

    private CommentResponse toResponse(Comment comment, Long viewerId, Map<Long, String> nicknames,
                                        Map<Long, List<Comment>> repliesByParentId) {
        List<CommentResponse> replies = repliesByParentId.getOrDefault(comment.getId(), List.of()).stream()
                .map(reply -> toResponse(reply, viewerId, nicknames, Map.of()))
                .toList();

        boolean visible = comment.isVisibleTo(viewerId);
        String content = visible ? comment.getContent() : "비밀 댓글입니다.";

        return new CommentResponse(
                comment.getId(),
                content,
                comment.getUserId(),
                nicknames.get(comment.getUserId()),
                comment.isOwnedBy(viewerId),
                comment.isSecret(),
                comment.getCreatedAt(),
                replies);
    }
}
