package com.duckspace.domain.exhibition.controller;

import com.duckspace.domain.exhibition.dto.request.AddItemRequest;
import com.duckspace.domain.exhibition.dto.request.CreateExhibitionRequest;
import com.duckspace.domain.exhibition.dto.request.UpdateExhibitionRequest;
import com.duckspace.domain.exhibition.dto.request.UpdatePositionRequest;
import com.duckspace.domain.exhibition.dto.request.UploadItemRequest;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "전시", description = "장식장과 자유 배치된 굿즈")
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

    @Operation(summary = "장식장 피드",
            description = """
                    검색 탭 기본 화면용 — 필터 없이 최신 등록순으로 전체 장식장을 돌려줍니다. limit 기본 10, 최대 50.
                    **비로그인도 호출할 수 있습니다.** 이때 likedByMe 는 전부 false 입니다.
                    """)
    @GetMapping
    public ApiResponse<List<ExhibitionSummaryResponse>> recent(@AuthenticationPrincipal AuthUser authUser,
                                                                 @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(exhibitionService.getRecent(limit, viewerId(authUser)));
    }

    @Operation(summary = "인기 전시장",
            description = """
                    좋아요가 많은 순입니다. 홈 화면에서 사용합니다. limit 기본 10, 최대 50.
                    **비로그인도 호출할 수 있습니다.** 이때 likedByMe 는 전부 false 입니다.
                    """)
    @GetMapping("/popular")
    public ApiResponse<List<ExhibitionSummaryResponse>> popular(@AuthenticationPrincipal AuthUser authUser,
                                                                 @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(exhibitionService.getPopular(limit, viewerId(authUser)));
    }

    @Operation(summary = "내 장식장 목록",
            description = """
                    마이페이지용. 만든 순서대로(오래된 것부터) 돌려줍니다. limit 기본 20, 최대 50.
                    응답 형태는 인기 전시장·검색과 같습니다.
                    """)
    @GetMapping("/me")
    public ApiResponse<List<ExhibitionSummaryResponse>> myExhibitions(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(exhibitionService.getMine(authUser.getUserId(), limit));
    }

    @Operation(summary = "유저의 대표 장식장",
            description = """
                    프로필 화면에서 "이 사람의 장식장" 버튼으로 이동할 때 씁니다.
                    장식장을 여러 개 가질 수 있어, 가장 먼저 만든(id가 가장 작은) 장식장을 대표로 돌려줍니다.
                    장식장이 하나도 없는 유저면 404(EXHIBITION_NOT_FOUND)입니다.
                    **비로그인도 호출할 수 있습니다.** 이때 likedByMe 는 false 입니다.
                    """)
    @GetMapping("/users/{userId:[0-9]+}/primary")
    public ApiResponse<ExhibitionSummaryResponse> primary(@AuthenticationPrincipal AuthUser authUser,
                                                            @PathVariable Long userId) {
        return ApiResponse.success(exhibitionService.getPrimary(userId, viewerId(authUser)));
    }

    @Operation(summary = "장식장 상세",
            description = """
                    배치된 굿즈를 전부 돌려줍니다. 각 굿즈의 posX/posY/width/height/rotation 으로 그리면 됩니다.
                    좌표와 크기는 배경 대비 비율(0.0~1.0)입니다.
                    본인 장식장이면 처리 중(PENDING)·실패(FAILED)한 굿즈도 함께 나옵니다.
                    **비로그인도 호출할 수 있습니다.** 이때 mine·likedByMe 는 false 이고
                    완료된 굿즈만 나옵니다.
                    """)
    // id 를 숫자로 못박아 위의 /me 와 겹치지 않게 합니다. (Spring 이 리터럴을 먼저 고르긴 하지만,
    //  나중에 /api/exhibitions/{다른 이름} 을 추가하는 사람이 헷갈리지 않도록 드러내 둡니다)
    @GetMapping("/{exhibitionId:[0-9]+}")
    public ApiResponse<ExhibitionDetailResponse> detail(@AuthenticationPrincipal AuthUser authUser,
                                                         @PathVariable Long exhibitionId) {
        return ApiResponse.success(exhibitionService.getDetail(exhibitionId, viewerId(authUser)));
    }

    /**
     * 로그인한 사용자면 id 를, 아니면 {@code null} 을 돌려줍니다.
     *
     * <p><b>공개 엔드포인트에서는 {@code authUser} 가 null 입니다.</b> 비로그인 요청에도
     * 인증 객체 자체는 채워지지만 principal 이 {@code "anonymousUser"} 라는 String 이라
     * {@code AuthUser} 로 캐스팅되지 못하고 null 이 들어옵니다.
     * 서비스단은 {@code viewerId == null} 을 "남의 장식장을 보는 사람" 으로 처리합니다.
     */
    private static Long viewerId(AuthUser authUser) {
        return authUser == null ? null : authUser.getUserId();
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
                    rotation 은 회전 각도(도)로 -180~180 이며, 0 이 기본이고 양수가 시계 방향입니다.
                    **처음 놓을 때 rotation 을 안 보내면 0(회전 없음)으로 저장됩니다.**
                    위치가 겹쳐도 막지 않습니다. 현재는 imageUrl 을 직접 받습니다.
                    """)
    @PostMapping("/{exhibitionId}/items")
    public ApiResponse<ExhibitionItemResponse> addItem(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long exhibitionId,
                                                        @Valid @RequestBody AddItemRequest request) {
        return ApiResponse.success(exhibitionItemService.add(exhibitionId, authUser.getUserId(), request));
    }

    @Operation(summary = "사진 업로드로 굿즈 배치",
            description = """
                    사진을 올려 장식장에 배치합니다. **접수만 하고 즉시 응답합니다.**
                    배경 제거와 후처리가 뒤에서 돌아가므로 응답의 status 는 PENDING 입니다.
                    응답의 itemId 로 단건 조회를 폴링해 READY 가 되면 화면에 반영하세요.
                    placement 의 좌표·크기는 배경 대비 비율(0.0~1.0)입니다.
                    JPG 또는 PNG, 10MB 이하만 받습니다.
                    """)
    @PostMapping(value = "/{exhibitionId}/items/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ExhibitionItemResponse> uploadItem(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long exhibitionId,
                                                           @RequestPart("image") MultipartFile image,
                                                           @Valid @RequestPart("data") UploadItemRequest request) {
        return ApiResponse.success(
                exhibitionItemService.upload(exhibitionId, authUser.getUserId(), image, request));
    }

    @Operation(summary = "굿즈 단건 조회 (폴링용)",
            description = """
                    업로드 후 status 가 READY 로 바뀔 때까지 이 API 를 폴링하세요.
                    처리 중(PENDING)·실패(FAILED)한 굿즈는 본인 장식장에서만 보입니다.
                    남의 장식장에서 조회하면 404 입니다.
                    """)
    @GetMapping("/{exhibitionId}/items/{itemId}")
    public ApiResponse<ExhibitionItemResponse> getItem(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long exhibitionId,
                                                        @PathVariable Long itemId) {
        return ApiResponse.success(
                exhibitionItemService.get(exhibitionId, itemId, viewerId(authUser)));
    }

    @Operation(summary = "실패한 굿즈 다시 처리",
            description = """
                    status 가 FAILED 인 굿즈를 다시 처리합니다. **사진을 다시 고를 필요가 없습니다.**
                    실패할 때 남겨둔 원본을 서버가 다시 태웁니다.
                    응답은 업로드와 같게 즉시 PENDING 으로 돌아오니, 단건 조회를 폴링하세요.
                    FAILED 가 아니면 409, 남겨둔 원본이 없으면 409 RETRY_SOURCE_MISSING 입니다
                    (이때는 삭제 후 다시 업로드해야 합니다).
                    """)
    @PostMapping("/{exhibitionId}/items/{itemId}/retry")
    public ApiResponse<ExhibitionItemResponse> retryItem(@AuthenticationPrincipal AuthUser authUser,
                                                          @PathVariable Long exhibitionId,
                                                          @PathVariable Long itemId) {
        return ApiResponse.success(
                exhibitionItemService.retry(exhibitionId, itemId, authUser.getUserId()));
    }

    @Operation(summary = "굿즈 위치·크기 저장",
            description = """
                    드래그로 옮기거나 크기를 조절하거나 회전한 결과를 저장합니다. 본인 장식장만 가능합니다.
                    posX/posY/width/height 는 필수라 보낸 값으로 통째 교체됩니다.
                    **rotation 만 예외로, 생략하면 기존 각도를 그대로 둡니다.**
                    회전을 되돌리려면 0 을 명시적으로 보내세요(생략과 0 은 다릅니다).
                    """)
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
