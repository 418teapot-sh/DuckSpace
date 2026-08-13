package com.duckspace.domain.exhibition.dto.request;

import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 굿즈를 장식장에 배치하는 요청.
 *
 * <p>이미지 파이프라인이 붙기 전 단계라 {@code imageUrl} 을 직접 받습니다.
 *
 * <p>모든 문자열 길이는 엔티티 컬럼 길이와 같은 상수를 씁니다. 검증이 빠지면 DB 제약 위반이
 * 400 이 아니라 500 으로 나갑니다.
 */
public record AddItemRequest(
        @NotNull @Valid PlacementRequest placement,
        @NotBlank @Size(max = ExhibitionItem.IMAGE_URL_MAX_LENGTH) String imageUrl,
        @NotBlank @Size(max = ExhibitionItem.ITEM_NAME_MAX_LENGTH) String itemName,
        @PositiveOrZero Integer price,
        @Size(max = ExhibitionItem.COMMENT_MAX_LENGTH) String comment
) {
}
