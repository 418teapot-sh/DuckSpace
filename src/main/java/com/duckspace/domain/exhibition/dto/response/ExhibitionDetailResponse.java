package com.duckspace.domain.exhibition.dto.response;

import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionItem;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 장식장 상세. 슬롯에 놓인 굿즈를 전부 담습니다.
 *
 * <p>슬롯 좌표는 프론트가 갖고 있으므로, 프론트는 {@code items} 의 {@code slotId} 로 위치를 찾습니다.
 */
public record ExhibitionDetailResponse(
        Long exhibitionId,
        String name,
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
                exhibition.getUserId(),
                exhibition.isOwnedBy(viewerId),
                likeCount,
                likedByMe,
                exhibition.getCreatedAt(),
                items.stream().map(ExhibitionItemResponse::from).toList()
        );
    }
}
