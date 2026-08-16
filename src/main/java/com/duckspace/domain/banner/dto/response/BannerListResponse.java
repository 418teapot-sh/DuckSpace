package com.duckspace.domain.banner.dto.response;

import java.util.List;

/** 캐러셀 인디케이터(1/5)용 — 노출 중인 배너 목록 + 총 개수 */
public record BannerListResponse(
        List<BannerResponse> banners,
        int totalCount
) {
    public static BannerListResponse of(List<BannerResponse> banners) {
        return new BannerListResponse(banners, banners.size());
    }
}