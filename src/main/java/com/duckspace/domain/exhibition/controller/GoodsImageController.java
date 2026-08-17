package com.duckspace.domain.exhibition.controller;

import com.duckspace.domain.exhibition.dto.response.GoodsImagePageResponse;
import com.duckspace.domain.exhibition.dto.response.GoodsImageResponse;
import com.duckspace.domain.exhibition.service.GoodsImageService;
import com.duckspace.global.auth.AuthUser;
import com.duckspace.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

/**
 * 굿즈 사진 보관함. 전부 본인 것만 다루므로 <b>모든 API 가 토큰 필수</b>입니다.
 */
@Tag(name = "보관함", description = "누끼 처리된 굿즈 사진 보관함 — 여기서 골라 장식장에 배치합니다")
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class GoodsImageController {

    private final GoodsImageService goodsImageService;

    @Operation(summary = "사진 업로드 (보관함으로)",
            description = """
                    사진을 보관함에 올립니다. **접수만 하고 즉시 응답합니다.**
                    배경 제거와 후처리가 뒤에서 돌아가므로 응답의 status 는 PENDING 입니다.
                    응답의 imageId 로 단건 조회를 폴링해 READY 가 되면 보관함에 표시하세요.
                    장식장 배치는 여기서 하지 않습니다 — READY 된 imageUrl 을
                    POST /api/exhibitions/{id}/items 에 넘기면 배치됩니다.
                    JPG 또는 PNG, 10MB 이하만 받습니다.
                    """)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<GoodsImageResponse> upload(@AuthenticationPrincipal AuthUser authUser,
                                                   @RequestPart("image") org.springframework.web.multipart.MultipartFile image) {
        return ApiResponse.success(goodsImageService.upload(authUser.getUserId(), image));
    }

    @Operation(summary = "내 보관함 목록",
            description = """
                    최신순 더보기 페이징입니다. 응답의 nextCursor 를 다음 요청의 cursor 에 넣으세요.
                    본인 보관함이라 처리 중(PENDING)·실패(FAILED)한 사진도 함께 나옵니다.
                    size 기본 20, 최대 50.
                    """)
    @GetMapping("/me")
    public ApiResponse<GoodsImagePageResponse> myImages(@AuthenticationPrincipal AuthUser authUser,
                                                         @RequestParam(required = false) Long cursor,
                                                         @RequestParam(required = false) Integer size) {
        return ApiResponse.success(goodsImageService.list(authUser.getUserId(), cursor, size));
    }

    @Operation(summary = "사진 단건 조회 (폴링용)",
            description = "업로드 후 status 가 READY 로 바뀔 때까지 이 API 를 폴링하세요.")
    // 숫자로 못박아 /me 와 겹치지 않게 합니다.
    @GetMapping("/{imageId:[0-9]+}")
    public ApiResponse<GoodsImageResponse> getImage(@AuthenticationPrincipal AuthUser authUser,
                                                     @PathVariable Long imageId) {
        return ApiResponse.success(goodsImageService.get(imageId, authUser.getUserId()));
    }

    @Operation(summary = "실패한 사진 다시 처리",
            description = """
                    status 가 FAILED 인 사진을 다시 처리합니다. **사진을 다시 고를 필요가 없습니다.**
                    응답은 업로드와 같게 즉시 PENDING 으로 돌아오니, 단건 조회를 폴링하세요.
                    FAILED 가 아니면 409, 남겨둔 원본이 없으면 409 RETRY_SOURCE_MISSING 입니다.
                    """)
    @PostMapping("/{imageId}/retry")
    public ApiResponse<GoodsImageResponse> retry(@AuthenticationPrincipal AuthUser authUser,
                                                  @PathVariable Long imageId) {
        return ApiResponse.success(goodsImageService.retry(imageId, authUser.getUserId()));
    }

    @Operation(summary = "보관함에서 사진 삭제",
            description = """
                    **장식장에 배치돼 있으면 409 IMAGE_IN_USE 로 거부됩니다** — 지우면 그 굿즈의
                    그림이 깨지기 때문입니다. 먼저 굿즈를 회수(삭제)한 뒤 지워주세요.
                    """)
    @DeleteMapping("/{imageId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser,
                                     @PathVariable Long imageId) {
        goodsImageService.delete(imageId, authUser.getUserId());
        return ApiResponse.noContent();
    }
}
