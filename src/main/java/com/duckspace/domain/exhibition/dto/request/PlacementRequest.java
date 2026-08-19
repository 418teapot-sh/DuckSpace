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
         * <p><b>{@code null} 은 "지정하지 않음" 이고, 생성과 수정에서 뜻이 갈립니다.</b>
         *
         * <ul>
         *   <li><b>생성</b>({@code POST /items}, {@code /items/upload}) — 0(회전 없음)으로 저장합니다.
         *       아직 지킬 이전 값이 없습니다.</li>
         *   <li><b>수정</b>({@code PATCH .../position}) — <b>기존 각도를 그대로 둡니다.</b>
         *       회전 UI 가 없는 화면은 현재 각도를 알 수도 없어서 같이 보낼 수가 없는데,
         *       그런 화면이 드래그만 해도 사용자가 돌려둔 각도가 0 으로 지워지면 안 됩니다.</li>
         * </ul>
         *
         * <p>회전을 되돌리려면 {@code 0} 을 <b>명시적으로</b> 보내세요.
         *
         * <p>이 구분은 실제로 겪은 버그를 고치면서 만든 규칙입니다. 예전 주석이 "안 보내면 0"
         * 이라고만 적혀 있어서, 이 DTO 만 보고 클라이언트를 짜면 그 버그가 되살아납니다.
         * ({@code ExhibitionItem#moveTo} 참고)
         */
        @DecimalMin("-180.0") @DecimalMax("180.0") Double rotation
) {
    public ExhibitionItem.Placement toPlacement() {
        return new ExhibitionItem.Placement(posX, posY, width, height, rotation);
    }
}
