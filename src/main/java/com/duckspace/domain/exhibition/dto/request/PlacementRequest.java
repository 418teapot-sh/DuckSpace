package com.duckspace.domain.exhibition.dto.request;

import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 굿즈를 어디에 얼마만큼 크게 놓을지.
 *
 * <p><b>배경 대비 비율(0.0 ~ 1.0)</b>입니다. 픽셀로 저장하면 화면 크기가 다른 기기에서 배치가
 * 어긋나므로 비율로 받습니다. {@code posX = 0.5} 는 배경 가로 한가운데를 뜻합니다.
 */
public record PlacementRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double posX,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double posY,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0") Double width,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0") Double height
) {
    public ExhibitionItem.Placement toPlacement() {
        return new ExhibitionItem.Placement(posX, posY, width, height);
    }
}
