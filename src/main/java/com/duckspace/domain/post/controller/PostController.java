package com.duckspace.domain.post.controller;

import com.duckspace.domain.post.dto.request.CasualPostRequest;
import com.duckspace.domain.post.dto.request.ExchangePostRequest;
import com.duckspace.domain.post.dto.request.ReportRequest;
import com.duckspace.domain.post.dto.response.CasualPostSummaryResponse;
import com.duckspace.domain.post.dto.response.ExchangePostSummaryResponse;
import com.duckspace.domain.post.dto.response.PostDetailResponse;
import com.duckspace.domain.post.service.LikeService;
import com.duckspace.domain.post.service.PostService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "덕톡라운지 게시글", description = "잡담/교환 게시판")
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final LikeService likeService;
    private final ReportService reportService;

    @Operation(summary = "잡담 글 작성")
    @PostMapping("/casual")
    public ApiResponse<Long> createCasual(@AuthenticationPrincipal AuthUser authUser,
                                           @Valid @RequestBody CasualPostRequest request) {
        return ApiResponse.success(postService.createCasual(authUser.getUserId(), request));
    }

    @Operation(summary = "잡담 글 목록",
            description = """
                    cursor를 비우면 최신 글부터, 값을 주면 그보다 오래된 글을 내려줍니다(마지막으로 받은 postId를 cursor에 넣으면 됨).
                    authorId를 주면 그 유저가 쓴 글만 나옵니다(마이페이지용). keyword와 같이 줄 수도 있으며 AND 조건입니다.
                    """)
    @GetMapping("/casual")
    public ApiResponse<List<CasualPostSummaryResponse>> listCasual(@RequestParam(required = false) String keyword,
                                                                     @RequestParam(required = false) Long cursor,
                                                                     @RequestParam(required = false) Integer size,
                                                                     @RequestParam(required = false) Long authorId) {
        return ApiResponse.success(postService.listCasual(keyword, cursor, size, authorId));
    }

    @Operation(summary = "교환 글 작성", description = "3단계(기본정보/내가 가진 굿즈/내가 원하는 굿즈) wizard를 한 번에 제출받습니다.")
    @PostMapping("/exchange")
    public ApiResponse<Long> createExchange(@AuthenticationPrincipal AuthUser authUser,
                                             @Valid @RequestBody ExchangePostRequest request) {
        return ApiResponse.success(postService.createExchange(authUser.getUserId(), request));
    }

    @Operation(summary = "교환 글 목록",
            description = """
                    cursor를 비우면 최신 글부터, 값을 주면 그보다 오래된 글을 내려줍니다(마지막으로 받은 postId를 cursor에 넣으면 됨).
                    authorId를 주면 그 유저가 쓴 글만 나옵니다(마이페이지용). keyword와 같이 줄 수도 있으며 AND 조건입니다.
                    """)
    @GetMapping("/exchange")
    public ApiResponse<List<ExchangePostSummaryResponse>> listExchange(@RequestParam(required = false) String keyword,
                                                                        @RequestParam(required = false) Long cursor,
                                                                        @RequestParam(required = false) Integer size,
                                                                        @RequestParam(required = false) Long authorId) {
        return ApiResponse.success(postService.listExchange(keyword, cursor, size, authorId));
    }

    @Operation(summary = "교환 완료 처리", description = "글 작성자만 가능합니다.")
    @PatchMapping("/exchange/{postId}/complete")
    public ApiResponse<Void> completeExchange(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long postId) {
        postService.completeExchange(postId, authUser.getUserId());
        return ApiResponse.noContent();
    }

    @Operation(summary = "게시글 상세", description = "잡담/교환 공용입니다. 응답의 exchangeInfo가 null이면 잡담 글입니다.")
    @GetMapping("/{postId}")
    public ApiResponse<PostDetailResponse> getDetail(@AuthenticationPrincipal AuthUser authUser,
                                                      @PathVariable Long postId) {
        return ApiResponse.success(postService.getDetail(postId, authUser.getUserId()));
    }

    @Operation(summary = "잡담 글 수정", description = "잡담 글만 지원합니다. 교환 글은 수정 대신 완료 처리만 가능합니다.")
    @PatchMapping("/{postId}")
    public ApiResponse<Void> updateCasual(@AuthenticationPrincipal AuthUser authUser,
                                           @PathVariable Long postId,
                                           @Valid @RequestBody CasualPostRequest request) {
        postService.updateCasual(postId, authUser.getUserId(), request);
        return ApiResponse.noContent();
    }

    @Operation(summary = "게시글 삭제", description = "잡담/교환 공용입니다. 작성자만 가능합니다.")
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long postId) {
        postService.delete(postId, authUser.getUserId());
        return ApiResponse.noContent();
    }

    @Operation(summary = "좋아요")
    @PostMapping("/{postId}/like")
    public ApiResponse<Void> like(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long postId) {
        likeService.like(authUser.getUserId(), postId);
        return ApiResponse.noContent();
    }

    @Operation(summary = "좋아요 취소")
    @DeleteMapping("/{postId}/like")
    public ApiResponse<Void> unlike(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long postId) {
        likeService.unlike(authUser.getUserId(), postId);
        return ApiResponse.noContent();
    }

    @Operation(summary = "게시글 신고", description = "본인 게시글은 신고할 수 없습니다. reason은 선택이며, 본문 자체를 생략해도 됩니다.")
    @PostMapping("/{postId}/report")
    public ApiResponse<Void> reportPost(@AuthenticationPrincipal AuthUser authUser,
                                         @PathVariable Long postId,
                                         @Valid @RequestBody(required = false) ReportRequest request) {
        reportService.reportPost(authUser.getUserId(), postId, request);
        return ApiResponse.noContent();
    }
}
