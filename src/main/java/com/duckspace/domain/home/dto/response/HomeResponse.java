package com.duckspace.domain.home.dto.response;

import com.duckspace.domain.banner.dto.response.BannerResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionSummaryResponse;
import com.duckspace.domain.popup.dto.response.PopupSummaryResponse;

import java.util.List;

public record HomeResponse(
        List<BannerResponse> banners,
        List<PopupSummaryResponse> upcomingPopups,
        List<ExhibitionSummaryResponse> popularExhibitions
) {
}