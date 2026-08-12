package com.duckspace.domain.exhibition.dto.response;

import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ItemStatus;

import java.time.LocalDateTime;

/**
 * @param status PENDING 이면 이미지 처리 중입니다. 프론트는 READY 가 될 때까지 폴링하세요.
 */
public record ExhibitionItemResponse(
        Long itemId,
        String slotId,
        String imageUrl,
        String itemName,
        String brand,
        String description,
        ItemStatus status,
        LocalDateTime createdAt
) {

    public static ExhibitionItemResponse from(ExhibitionItem item) {
        return new ExhibitionItemResponse(
                item.getId(),
                item.getSlotId(),
                item.getImageUrl(),
                item.getItemName(),
                item.getBrand(),
                item.getDescription(),
                item.getStatus(),
                item.getCreatedAt()
        );
    }
}
