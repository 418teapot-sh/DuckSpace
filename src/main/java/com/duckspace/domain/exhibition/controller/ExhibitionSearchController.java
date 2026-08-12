package com.duckspace.domain.exhibition.controller;

import com.duckspace.domain.exhibition.dto.response.ExhibitionSummaryResponse;
import com.duckspace.domain.exhibition.service.ExhibitionService;
import com.duckspace.global.auth.AuthUser;
import com.duckspace.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 검색 탭의 "전시" 결과.
 *
 * <p>덕톡라운지 검색과 통합해 프론트에서 게시글/유저/전시 탭으로 붙이기로 해서,
 * 경로를 {@code /api/search/*} 아래에 둡니다.
 */
@Tag(name = "검색", description = "통합 검색 — 전시 탭")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class ExhibitionSearchController {

    private final ExhibitionService exhibitionService;

    @Operation(summary = "전시 검색",
            description = """
                    굿즈 이름·브랜드가 키워드와 겹치는 장식장을 돌려줍니다.
                    키워드가 비어 있으면 빈 목록입니다. limit 기본 10, 최대 50.
                    """)
    @GetMapping("/exhibitions")
    public ApiResponse<List<ExhibitionSummaryResponse>> searchExhibitions(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(exhibitionService.search(keyword, limit, authUser.getUserId()));
    }
}
