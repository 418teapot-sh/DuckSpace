package com.duckspace.domain.exhibition.dto.response;

import com.duckspace.domain.exhibition.entity.GoodsImage;
import com.duckspace.domain.exhibition.entity.ItemStatus;

import java.time.LocalDateTime;

/**
 * 보관함의 사진 하나.
 *
 * @param imageUrl 처리(누끼)된 이미지 주소. {@code PENDING} 중엔 null,
 *                 {@code FAILED} 면 배경이 제거되지 않은 원본일 수 있습니다.
 * @param status   {@code PENDING} 이면 처리 중입니다. 프론트는 READY 가 될 때까지 폴링하세요.
 */
public record GoodsImageResponse(
        Long imageId,
        String imageUrl,
        ItemStatus status,
        LocalDateTime createdAt
) {

    public static GoodsImageResponse from(GoodsImage image) {
        return new GoodsImageResponse(
                image.getId(),
                image.getImageUrl(),
                image.getStatus(),
                image.getCreatedAt()
        );
    }
}
