package com.duckspace.domain.exhibition.dto.response;

import com.duckspace.domain.exhibition.entity.Exhibition;

/**
 * 목록 카드용 요약. 인기 전시장과 검색 결과가 같은 모양을 씁니다.
 *
 * @param thumbnailUrl 대표 이미지(가장 먼저 배치된 굿즈). 아직 굿즈가 없으면 null 입니다.
 */
public record ExhibitionSummaryResponse(
        Long exhibitionId,
        String name,
        String themeCode,
        Long ownerId,
        String thumbnailUrl,
        long likeCount,
        boolean likedByMe
) {

    public static ExhibitionSummaryResponse of(Exhibition exhibition, String thumbnailUrl,
                                                long likeCount, boolean likedByMe) {
        return new ExhibitionSummaryResponse(
                exhibition.getId(),
                exhibition.getName(),
                exhibition.getThemeCode(),
                exhibition.getUserId(),
                thumbnailUrl,
                likeCount,
                likedByMe
        );
    }
}
