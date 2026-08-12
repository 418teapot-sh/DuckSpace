package com.duckspace.domain.exhibition.dto.response;

import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionItem;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 장식장 상세. 배치된 굿즈를 전부 담습니다.
 *
 * <p>배경 이미지는 프론트가 {@code themeCode} 로 찾고, 굿즈는 각자의 좌표·크기로 그립니다.
 */
public record ExhibitionDetailResponse(
        Long exhibitionId,
        String name,
        String themeCode,
        Long ownerId,
        boolean mine,
        long likeCount,
        boolean likedByMe,
        LocalDateTime createdAt,
        List<ExhibitionItemResponse> items
) {

    public static ExhibitionDetailResponse of(Exhibition exhibition, Long viewerId,
                                               long likeCount, boolean likedByMe,
                                               List<ExhibitionItem> items) {
        return new ExhibitionDetailResponse(
                exhibition.getId(),
                exhibition.getName(),
                exhibition.getThemeCode(),
                exhibition.getUserId(),
                exhibition.isOwnedBy(viewerId),
                likeCount,
                likedByMe,
                exhibition.getCreatedAt(),
                items.stream().map(ExhibitionItemResponse::from).toList()
        );
    }
}
