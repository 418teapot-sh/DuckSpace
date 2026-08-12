package com.duckspace.domain.exhibition.dto.request;

import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 슬롯에 굿즈를 배치하는 요청.
 *
 * <p>이미지 업로드 파이프라인(배경 제거·S3)이 붙기 전 단계라 {@code imageUrl} 을 직접 받습니다.
 * 파이프라인이 붙으면 파일 업로드 엔드포인트가 추가되고, 이 API 는 그대로 남습니다.
 *
 * <p>모든 문자열 길이는 엔티티 컬럼 길이와 같은 상수를 씁니다. 검증이 빠지면 DB 제약 위반이
 * 400 이 아니라 500 으로 나갑니다.
 */
public record AddItemRequest(
        @NotBlank @Size(max = ExhibitionItem.SLOT_ID_MAX_LENGTH) String slotId,
        @NotBlank @Size(max = ExhibitionItem.IMAGE_URL_MAX_LENGTH) String imageUrl,
        @NotBlank @Size(max = ExhibitionItem.ITEM_NAME_MAX_LENGTH) String itemName,
        @Size(max = ExhibitionItem.BRAND_MAX_LENGTH) String brand,
        @Size(max = ExhibitionItem.DESCRIPTION_MAX_LENGTH) String description
) {
}
