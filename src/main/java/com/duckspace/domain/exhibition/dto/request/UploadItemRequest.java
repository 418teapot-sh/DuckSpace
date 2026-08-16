package com.duckspace.domain.exhibition.dto.request;

import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 사진 업로드로 굿즈를 배치하는 요청의 텍스트 부분.
 *
 * <p>{@code imageUrl} 이 없는 것이 {@link AddItemRequest} 와의 차이입니다.
 * 이미지는 멀티파트 파일로 따로 오고, 처리 후 서버가 주소를 채웁니다.
 */
public record UploadItemRequest(
        @NotNull @Valid PlacementRequest placement,
        @NotBlank @Size(max = ExhibitionItem.ITEM_NAME_MAX_LENGTH) String itemName,
        @PositiveOrZero Integer price,
        @Size(max = ExhibitionItem.COMMENT_MAX_LENGTH) String comment
) {
}
