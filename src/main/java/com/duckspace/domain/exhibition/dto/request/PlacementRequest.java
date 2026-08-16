package com.duckspace.domain.exhibition.dto.request;

import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 굿즈를 어디에 얼마만큼 크게, 얼마나 돌려서 놓을지.
 *
 * <p>위치와 크기는 <b>배경 대비 비율(0.0 ~ 1.0)</b>입니다. 픽셀로 저장하면 화면 크기가 다른
 * 기기에서 배치가 어긋나므로 비율로 받습니다. {@code posX = 0.5} 는 배경 가로 한가운데를 뜻합니다.
 *
 * <p>회전만 비율이 아니라 <b>각도(도)</b>입니다. 각도는 화면 크기와 무관하기 때문입니다.
 */
public record PlacementRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double posX,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double posY,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0") Double width,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0") Double height,

        /**
         * 회전 각도. {@code -180 ~ 180}, 양수가 시계 방향입니다.
         *
         * <p><b>보내지 않으면 0(회전 없음)</b>으로 저장합니다. 회전 기능이 생기기 전에 만들어진
         * 화면이 그대로 동작해야 해서 선택 항목으로 뒀습니다.
         */
        @DecimalMin("-180.0") @DecimalMax("180.0") Double rotation
) {
    public ExhibitionItem.Placement toPlacement() {
        return new ExhibitionItem.Placement(posX, posY, width, height, rotation);
    }
}
