package com.duckspace.domain.post.controller;

import com.duckspace.domain.post.dto.request.ExchangeApplicationRequest;
import com.duckspace.domain.post.dto.response.ExchangeApplicationResponse;
import com.duckspace.domain.post.service.ExchangeApplicationService;
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

@Tag(name = "교환 신청", description = "교환 게시글 신청→수락→완료 매칭. 후기/신뢰도 점수는 프론트 목업이라 여기 없습니다.")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExchangeApplicationController {

    private final ExchangeApplicationService exchangeApplicationService;

    @Operation(summary = "교환 신청",
            description = "본인 글에는 신청할 수 없고, 이미 교환 완료된 글에는 신청할 수 없습니다. 같은 글에 대기중(APPLIED)이거나 수락된(ACCEPTED) 신청이 이미 있으면 또 신청할 수 없습니다.")
    @PostMapping("/posts/exchange/{postId}/applications")
    public ApiResponse<Long> apply(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable Long postId,
                                    @Valid @RequestBody ExchangeApplicationRequest request) {
        return ApiResponse.success(exchangeApplicationService.apply(authUser.getUserId(), postId, request));
    }

    @Operation(summary = "게시글별 신청 목록",
            description = "글쓴이만 조회할 수 있습니다. cursor를 비우면 최신 신청부터, 값을 주면 그보다 오래된 신청을 내려줍니다(마지막으로 받은 id를 cursor에 넣으면 됨).")
    @GetMapping("/posts/exchange/{postId}/applications")
    public ApiResponse<List<ExchangeApplicationResponse>> listByPost(@AuthenticationPrincipal AuthUser authUser,
                                                                       @PathVariable Long postId,
                                                                       @RequestParam(required = false) Long cursor,
                                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.success(exchangeApplicationService.listByPost(postId, authUser.getUserId(), cursor, size));
    }

    @Operation(summary = "내 신청함",
            description = "filter=sent면 내가 신청한 것, filter=received면 내 글에 들어온 신청입니다. cursor/size는 게시글별 신청 목록과 같은 규칙입니다.")
    @GetMapping("/applications")
    public ApiResponse<List<ExchangeApplicationResponse>> listMine(@AuthenticationPrincipal AuthUser authUser,
                                                                      @RequestParam String filter,
                                                                      @RequestParam(required = false) Long cursor,
                                                                      @RequestParam(required = false) Integer size) {
        return ApiResponse.success(exchangeApplicationService.listMine(authUser.getUserId(), filter, cursor, size));
    }

    @Operation(summary = "신청 수락", description = "글쓴이만 가능합니다.")
    @PatchMapping("/applications/{id}/accept")
    public ApiResponse<Void> accept(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        exchangeApplicationService.accept(id, authUser.getUserId());
        return ApiResponse.noContent();
    }

    @Operation(summary = "신청 거절", description = "글쓴이만 가능합니다.")
    @PatchMapping("/applications/{id}/reject")
    public ApiResponse<Void> reject(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        exchangeApplicationService.reject(id, authUser.getUserId());
        return ApiResponse.noContent();
    }

    @Operation(summary = "신청 완료 처리", description = "글쓴이만 가능합니다(신청자는 완료 처리할 수 없습니다). 게시글의 교환 상태도 함께 완료로 전환됩니다.")
    @PatchMapping("/applications/{id}/complete")
    public ApiResponse<Void> complete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        exchangeApplicationService.complete(id, authUser.getUserId());
        return ApiResponse.noContent();
    }

    @Operation(summary = "신청 취소", description = "신청자 본인만 가능합니다.")
    @DeleteMapping("/applications/{id}")
    public ApiResponse<Void> cancel(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        exchangeApplicationService.cancel(id, authUser.getUserId());
        return ApiResponse.noContent();
    }
}
