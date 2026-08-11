package com.duckspace.domain.exhibition.dto.response;

import com.duckspace.domain.exhibition.entity.ExhibitionItem;

public record ExhibitionItemResponse(
        Long id,
        String name,
        String imageUrl,
        String description,
        int sortOrder
) {
    public static ExhibitionItemResponse from(ExhibitionItem item) {
        return new ExhibitionItemResponse(
                item.getId(),
                item.getName(),
                item.getImageUrl(),
                item.getDescription(),
                item.getSortOrder()
        );
    }
}