package com.duckspace.domain.post.controller;

import com.duckspace.domain.post.dto.request.CommentRequest;
import com.duckspace.domain.post.dto.request.ReportRequest;
import com.duckspace.domain.post.dto.response.CommentResponse;
import com.duckspace.domain.post.service.CommentService;
import com.duckspace.domain.post.service.ReportService;
import com.duckspace.global.auth.AuthUser;
import com.duckspace.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "덕톡라운지 댓글", description = "대댓글 1단계, 비밀댓글 지원")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final ReportService reportService;

    @Operation(summary = "댓글/답글 작성", description = "parentId를 주면 답글입니다. 답글에는 답글을 달 수 없습니다.")
    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<Long> create(@AuthenticationPrincipal AuthUser authUser,
                                     @PathVariable Long postId,
                                     @Valid @RequestBody CommentRequest request) {
        return ApiResponse.success(commentService.create(authUser.getUserId(), postId, request));
    }

    @Operation(summary = "댓글 목록", description = "최상위 댓글마다 답글이 중첩된 형태로 내려줍니다. 비밀댓글은 작성자/게시글 주인이 아니면 내용이 가려집니다. " +
            "cursor를 비우면 처음부터, 값을 주면 그보다 나중에 달린 최상위 댓글부터 내려줍니다(마지막으로 받은 댓글 id를 cursor에 넣으면 됨).")
    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<List<CommentResponse>> list(@AuthenticationPrincipal AuthUser authUser,
                                                     @PathVariable Long postId,
                                                     @RequestParam(required = false) Long cursor,
                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.success(commentService.list(postId, authUser.getUserId(), cursor, size));
    }

    @Operation(summary = "댓글 삭제", description = "작성자만 가능합니다. 답글이 달린 댓글을 지우면 답글도 함께 삭제됩니다.")
    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long commentId) {
        commentService.delete(commentId, authUser.getUserId());
        return ApiResponse.noContent();
    }

    @Operation(summary = "댓글 신고", description = "본인 댓글은 신고할 수 없습니다. reason은 선택이며, 본문 자체를 생략해도 됩니다.")
    @PostMapping("/comments/{commentId}/report")
    public ApiResponse<Void> reportComment(@AuthenticationPrincipal AuthUser authUser,
                                            @PathVariable Long commentId,
                                            @Valid @RequestBody(required = false) ReportRequest request) {
        reportService.reportComment(authUser.getUserId(), commentId, request);
        return ApiResponse.noContent();
    }
}
