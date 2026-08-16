package com.duckspace.domain.post.dto.request;

import com.duckspace.domain.post.entity.ExchangeApplication;
import com.duckspace.domain.post.entity.ItemCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 교환 신청. offeredItemName만 필수이고 나머지는 선택입니다. */
public record ExchangeApplicationRequest(
        @NotBlank @Size(max = ExchangeApplication.OFFERED_ITEM_NAME_MAX_LENGTH) String offeredItemName,
        @Size(max = ExchangeApplication.OFFERED_IMAGE_URL_MAX_LENGTH) String offeredImageUrl,
        @Size(max = ExchangeApplication.OFFERED_BRAND_MAX_LENGTH) String offeredBrand,
        ItemCondition offeredCondition,
        @Size(max = ExchangeApplication.MESSAGE_MAX_LENGTH) String message
) {
}
