package com.duckspace.domain.exhibition.dto.response;

import com.duckspace.domain.exhibition.entity.Exhibition;

import java.util.List;

public record ExhibitionResponse(
        Long id,
        String title,
        String description,
        String thumbnailUrl,
        long viewCount,
        long likeCount,
        List<ExhibitionItemResponse> items
) {
    public static ExhibitionResponse from(Exhibition exhibition) {
        return new ExhibitionResponse(
                exhibition.getId(),
                exhibition.getTitle(),
                exhibition.getDescription(),
                exhibition.getThumbnailUrl(),
                exhibition.getViewCount(),
                exhibition.getLikeCount(),
                exhibition.getItems().stream()
                        .map(ExhibitionItemResponse::from)
                        .toList()
        );
    }
}