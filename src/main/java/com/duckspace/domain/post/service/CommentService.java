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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

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

    /** 최상위 댓글 기준으로 커서 페이지네이션합니다. cursor를 비우면 처음부터, 값을 주면 그 이후 댓글을 내려줍니다. */
    public List<CommentResponse> list(Long postId, Long viewerId, Long cursor, Integer size) {
        postService.getPost(postId);
        List<Comment> topLevel = commentRepository.findTopLevelByPostId(postId, cursor, pageable(size));
        if (topLevel.isEmpty()) {
            return List.of();
        }

        List<Long> topLevelIds = topLevel.stream().map(Comment::getId).toList();
        List<Comment> replies = commentRepository.findByParent_IdInOrderByIdAsc(topLevelIds);

        List<Comment> all = new ArrayList<>(topLevel);
        all.addAll(replies);
        List<Long> authorIds = all.stream().map(Comment::getUserId).distinct().toList();
        Map<Long, String> nicknames = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        Map<Long, List<Comment>> repliesByParentId = replies.stream()
                .collect(Collectors.groupingBy(reply -> reply.getParent().getId()));

        return topLevel.stream()
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

    private Pageable pageable(Integer size) {
        return PageRequest.of(0, normalizeSize(size));
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
