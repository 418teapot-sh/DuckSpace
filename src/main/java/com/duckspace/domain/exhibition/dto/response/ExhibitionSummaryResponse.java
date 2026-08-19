package com.duckspace.domain.exhibition.dto.response;

import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionItem;

import java.util.List;

/**
 * 목록 카드용 요약. 인기 전시장·검색 결과·장식장 피드·내 장식장·대표 장식장이 전부 같은 모양을 씁니다.
 *
 * @param thumbnailUrl 대표 이미지(가장 먼저 배치된 굿즈, {@code items} 의 첫 번째와 같은 이미지).
 *                     아직 굿즈가 없으면 null 입니다.
 * @param items        배치된 굿즈. 카드 미리보기에서 배경 위에 그대로 그리는 용도입니다.
 *                     굿즈가 없으면 빈 목록이고, 개수 상한은 {@code ExhibitionService} 를
 *                     참고하세요(응답 크기 보호용, 전체 개수가 아닙니다).
 */
public record ExhibitionSummaryResponse(
        Long exhibitionId,
        String name,
        String themeCode,
        Long ownerId,
        String thumbnailUrl,
        List<ExhibitionItemPreviewResponse> items,
        long likeCount,
        boolean likedByMe
) {

    public static ExhibitionSummaryResponse of(Exhibition exhibition, String thumbnailUrl,
                                                List<ExhibitionItem> items,
                                                long likeCount, boolean likedByMe) {
        return new ExhibitionSummaryResponse(
                exhibition.getId(),
                exhibition.getName(),
                exhibition.getThemeCode(),
                exhibition.getUserId(),
                thumbnailUrl,
                items.stream().map(ExhibitionItemPreviewResponse::from).toList(),
                likeCount,
                likedByMe
        );
    }
}
