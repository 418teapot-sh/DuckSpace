package com.duckspace.domain.exhibition.dto.response;

import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ItemStatus;

import java.time.LocalDateTime;

/**
 * 배치된 굿즈 하나.
 *
 * <p>{@code posX/posY/width/height} 는 <b>배경 대비 비율(0.0 ~ 1.0)</b>입니다.
 *
 * @param status PENDING 이면 이미지 처리 중입니다. 프론트는 READY 가 될 때까지 폴링하세요.
 */
public record ExhibitionItemResponse(
        Long itemId,
        Double posX,
        Double posY,
        Double width,
        Double height,
        String imageUrl,
        String itemName,
        Integer price,
        String comment,
        ItemStatus status,
        LocalDateTime createdAt
) {

    public static ExhibitionItemResponse from(ExhibitionItem item) {
        return new ExhibitionItemResponse(
                item.getId(),
                item.getPosX(),
                item.getPosY(),
                item.getWidth(),
                item.getHeight(),
                item.getImageUrl(),
                item.getItemName(),
                item.getPrice(),
                item.getComment(),
                item.getStatus(),
                item.getCreatedAt()
        );
    }
}
