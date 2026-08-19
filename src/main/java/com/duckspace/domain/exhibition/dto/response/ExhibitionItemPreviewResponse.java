package com.duckspace.domain.exhibition.dto.response;

import com.duckspace.domain.exhibition.entity.ExhibitionItem;

/**
 * 목록 카드 미리보기에 배치된 굿즈 하나. {@link ExhibitionItemResponse}(상세용)의 슬림 버전입니다.
 *
 * <p>{@code posX/posY/width/height} 는 <b>배경 대비 비율(0.0 ~ 1.0)</b>입니다.
 *
 * <p><b>상세용과 달리 {@code price}·{@code comment}·{@code itemName}·{@code createdAt}·
 * {@code status} 는 뺐습니다.</b> 카드에 배경 위 굿즈를 그리는 데는 좌표·이미지만 필요하고,
 * 특히 {@code price}(구매가)는 지금까지 상세 응답에만 있던 걸 인증 없이 열린 목록
 * 엔드포인트(인기·피드·검색)까지 그대로 실어 보내는 셈이라 노출 범위가 넓어집니다(#77 리뷰).
 *
 * @param rotation 회전 각도(도). {@code -180 ~ 180}, 0 이 기본이고 양수가 시계 방향입니다.
 */
public record ExhibitionItemPreviewResponse(
        Long itemId,
        Double posX,
        Double posY,
        Double width,
        Double height,
        Double rotation,
        String imageUrl
) {

    public static ExhibitionItemPreviewResponse from(ExhibitionItem item) {
        return new ExhibitionItemPreviewResponse(
                item.getId(),
                item.getPosX(),
                item.getPosY(),
                item.getWidth(),
                item.getHeight(),
                item.getRotation(),
                item.getImageUrl()
        );
    }
}
