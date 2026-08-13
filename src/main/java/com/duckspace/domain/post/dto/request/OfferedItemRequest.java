package com.duckspace.domain.post.dto.request;

import com.duckspace.domain.post.entity.ItemCondition;
import com.duckspace.domain.post.entity.TradeItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 교환 글 2단계 — 내가 가진 굿즈. */
public record OfferedItemRequest(
        @Size(max = TradeItem.IMAGE_URL_MAX_LENGTH) String imageUrl,
        @NotBlank @Size(max = TradeItem.ITEM_NAME_MAX_LENGTH) String itemName,
        @Size(max = TradeItem.BRAND_MAX_LENGTH) String brand,
        @NotNull ItemCondition condition
) {
}
