package com.duckspace.domain.exhibition.dto.response;

import com.duckspace.domain.exhibition.entity.Exhibition;

/** 전시 목록/홈 카드용 — 아이템 배치 없이 대표 정보만 */
public record ExhibitionSummaryResponse(
        Long id,
        String title,
        String thumbnailUrl,
        long viewCount,
        long likeCount
) {
    public static ExhibitionSummaryResponse from(Exhibition exhibition) {
        return new ExhibitionSummaryResponse(
                exhibition.getId(),
                exhibition.getTitle(),
                exhibition.getThumbnailUrl(),
                exhibition.getViewCount(),
                exhibition.getLikeCount()
        );
    }
}