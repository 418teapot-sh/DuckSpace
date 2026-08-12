package com.duckspace.domain.exhibition.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * 아이템 이미지 처리 상태.
 *
 * <p>이미지 업로드는 배경 제거·저장을 거쳐야 해서 수 초가 걸립니다. 업로드 요청은 즉시 응답하고
 * 처리는 뒤에서 돌리기 위해 상태를 둡니다. 프론트는 {@code READY} 가 될 때까지 폴링합니다.
 */
public enum ItemStatus {
    /** 업로드 접수됨. 배경 제거·저장 진행 중. */
    PENDING,
    /** 처리 완료. 화면에 표시 가능. */
    READY,
    /** 처리 실패. 재시도하거나 삭제해야 합니다. */
    FAILED;

    /**
     * 보는 사람에 따라 노출할 상태.
     *
     * <p>주인은 자기 장식장을 편집하므로 처리 중·실패한 굿즈도 보여야 조치할 수 있습니다.
     * 반면 남의 장식장을 구경할 때 처리 중인 빈 칸이 보이면 이상하므로 완료된 것만 보여줍니다.
     */
    public static Set<ItemStatus> visibleTo(boolean owner) {
        return owner ? EnumSet.allOf(ItemStatus.class) : EnumSet.of(READY);
    }
}
