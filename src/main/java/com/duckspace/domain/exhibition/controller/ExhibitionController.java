package com.duckspace.domain.exhibition.controller;

import com.duckspace.domain.exhibition.dto.request.AddItemRequest;
import com.duckspace.domain.exhibition.dto.request.CreateExhibitionRequest;
import com.duckspace.domain.exhibition.dto.request.UpdateExhibitionRequest;
import com.duckspace.domain.exhibition.dto.request.UpdatePositionRequest;
import com.duckspace.domain.exhibition.dto.response.ExhibitionDetailResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionItemPageResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionItemResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionSummaryResponse;
import com.duckspace.domain.exhibition.service.ExhibitionItemService;
import com.duckspace.domain.exhibition.service.ExhibitionLikeService;
import com.duckspace.domain.exhibition.service.ExhibitionService;
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

@Tag(name = "전시", description = "장식장과 슬롯에 배치된 굿즈")
@RestController
@RequestMapping("/api/exhibitions")
@RequiredArgsConstructor
public class ExhibitionController {

    private final ExhibitionService exhibitionService;
    private final ExhibitionItemService exhibitionItemService;
    private final ExhibitionLikeService exhibitionLikeService;

    @Operation(summary = "장식장 생성")
    @PostMapping
    public ApiResponse<ExhibitionDetailResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                                         @Valid @RequestBody CreateExhibitionRequest request) {
        return ApiResponse.success(exhibitionService.create(authUser.getUserId(), request));
    }

    @Operation(summary = "인기 전시장",
            description = "좋아요가 많은 순입니다. 홈 화면에서 사용합니다. limit 기본 10, 최대 50.")
    @GetMapping("/popular")
    public ApiResponse<List<ExhibitionSummaryResponse>> popular(@AuthenticationPrincipal AuthUser authUser,
                                                                 @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(exhibitionService.getPopular(limit, authUser.getUserId()));
    }

    @Operation(summary = "장식장 상세",
            description = "슬롯에 놓인 굿즈를 전부 돌려줍니다. 프론트는 items 의 slotId 로 배치 위치를 찾습니다.")
    @GetMapping("/{exhibitionId}")
    public ApiResponse<ExhibitionDetailResponse> detail(@AuthenticationPrincipal AuthUser authUser,
                                                         @PathVariable Long exhibitionId) {
        return ApiResponse.success(exhibitionService.getDetail(exhibitionId, authUser.getUserId()));
    }

    @Operation(summary = "장식장 이름 수정", description = "본인 장식장만 수정할 수 있습니다.")
    @PatchMapping("/{exhibitionId}")
    public ApiResponse<ExhibitionDetailResponse> rename(@AuthenticationPrincipal AuthUser authUser,
                                                         @PathVariable Long exhibitionId,
                                                         @Valid @RequestBody UpdateExhibitionRequest request) {
        return ApiResponse.success(exhibitionService.rename(exhibitionId, authUser.getUserId(), request));
    }

    @Operation(summary = "장식장 삭제", description = "안에 놓인 굿즈와 좋아요도 함께 삭제됩니다.")
    @DeleteMapping("/{exhibitionId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser,
                                     @PathVariable Long exhibitionId) {
        exhibitionService.delete(exhibitionId, authUser.getUserId());
        return ApiResponse.noContent();
    }

    @Operation(summary = "굿즈 배치",
            description = """
                    자유 배치입니다. placement 의 좌표·크기는 배경 대비 비율(0.0~1.0)입니다.
                    위치가 겹쳐도 막지 않습니다. 현재는 imageUrl 을 직접 받습니다.
                    """)
    @PostMapping("/{exhibitionId}/items")
    public ApiResponse<ExhibitionItemResponse> addItem(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long exhibitionId,
                                                        @Valid @RequestBody AddItemRequest request) {
        return ApiResponse.success(exhibitionItemService.add(exhibitionId, authUser.getUserId(), request));
    }

    @Operation(summary = "굿즈 위치·크기 저장",
            description = "드래그로 옮기거나 크기를 조절한 결과를 저장합니다. 본인 장식장만 가능합니다.")
    @PatchMapping("/{exhibitionId}/items/{itemId}/position")
    public ApiResponse<ExhibitionItemResponse> updateItemPosition(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long exhibitionId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdatePositionRequest request) {
        return ApiResponse.success(
                exhibitionItemService.updatePosition(exhibitionId, itemId, authUser.getUserId(), request));
    }

    @Operation(summary = "전시된 굿즈 그리드",
            description = """
                    최신순 더보기 페이징입니다. 응답의 nextCursor 를 다음 요청의 cursor 로 넣으세요.
                    본인 장식장이면 처리 중(PENDING)·실패(FAILED)한 굿즈도 함께 나옵니다.
                    """)
    @GetMapping("/{exhibitionId}/items")
    public ApiResponse<ExhibitionItemPageResponse> listItems(@AuthenticationPrincipal AuthUser authUser,
                                                              @PathVariable Long exhibitionId,
                                                              @RequestParam(required = false) Long cursor,
                                                              @RequestParam(required = false) Integer size) {
        return ApiResponse.success(
                exhibitionItemService.list(exhibitionId, authUser.getUserId(), cursor, size));
    }

    @Operation(summary = "전시된 굿즈 삭제")
    @DeleteMapping("/{exhibitionId}/items/{itemId}")
    public ApiResponse<Void> deleteItem(@AuthenticationPrincipal AuthUser authUser,
                                         @PathVariable Long exhibitionId,
                                         @PathVariable Long itemId) {
        exhibitionItemService.delete(exhibitionId, itemId, authUser.getUserId());
        return ApiResponse.noContent();
    }

    @Operation(summary = "좋아요", description = "여러 번 호출해도 결과가 같습니다.")
    @PostMapping("/{exhibitionId}/like")
    public ApiResponse<Void> like(@AuthenticationPrincipal AuthUser authUser,
                                   @PathVariable Long exhibitionId) {
        exhibitionLikeService.like(exhibitionId, authUser.getUserId());
        return ApiResponse.noContent();
    }

    @Operation(summary = "좋아요 취소", description = "누른 적이 없어도 성공합니다.")
    @DeleteMapping("/{exhibitionId}/like")
    public ApiResponse<Void> unlike(@AuthenticationPrincipal AuthUser authUser,
                                     @PathVariable Long exhibitionId) {
        exhibitionLikeService.unlike(exhibitionId, authUser.getUserId());
        return ApiResponse.noContent();
    }
}
