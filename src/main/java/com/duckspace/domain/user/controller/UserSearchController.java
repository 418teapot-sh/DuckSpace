package com.duckspace.domain.user.controller;

import com.duckspace.domain.user.dto.request.RecordUserSearchRequest;
import com.duckspace.domain.user.dto.response.UserSearchResponse;
import com.duckspace.domain.user.service.UserSearchService;
import com.duckspace.global.auth.AuthUser;
import com.duckspace.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 검색 탭의 "유저" 결과. 덕톡라운지/전시 검색과 통합해서 프론트가 하나의 검색 화면에서
 * 붙이기로 해서, 경로를 {@code /api/search/*} 아래에 둡니다
 * ({@code com.duckspace.domain.exhibition.controller.ExhibitionSearchController} 참고).
 */
@Tag(name = "검색", description = "통합 검색 — 유저 탭 + 최근 검색 내역")
@RestController
@RequestMapping("/api/search/users")
@RequiredArgsConstructor
public class UserSearchController {

    private final UserSearchService userSearchService;

    @Operation(summary = "유저 검색",
            description = """
                    닉네임이 키워드와 겹치는 유저를 돌려줍니다.
                    키워드가 비어 있으면 빈 목록입니다. limit 기본 10, 최대 50.
                    **비로그인도 호출할 수 있습니다.**
                    """)
    @GetMapping
    public ApiResponse<List<UserSearchResponse>> search(@RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(userSearchService.search(keyword, limit));
    }

    @Operation(summary = "최근 검색 내역", description = "본인이 클릭한 검색 결과 최대 3개를 최신순으로 돌려줍니다.")
    @GetMapping("/history")
    public ApiResponse<List<UserSearchResponse>> getHistory(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(userSearchService.getHistory(authUser.getUserId()));
    }

    @Operation(summary = "검색 결과 클릭 기록",
            description = "검색 결과에서 유저를 클릭했을 때 호출하세요. 타이핑만으로는 기록되지 않습니다.")
    @PostMapping("/history")
    public ApiResponse<Void> recordHistory(@AuthenticationPrincipal AuthUser authUser,
                                            @Valid @RequestBody RecordUserSearchRequest request) {
        userSearchService.record(authUser.getUserId(), request.targetUserId());
        return ApiResponse.noContent();
    }

    @Operation(summary = "검색 내역 전체 삭제")
    @DeleteMapping("/history")
    public ApiResponse<Void> clearHistory(@AuthenticationPrincipal AuthUser authUser) {
        userSearchService.clearHistory(authUser.getUserId());
        return ApiResponse.noContent();
    }
}
