package com.duckspace.global.support;

/**
 * 페이지 크기·조회 개수 보정.
 *
 * <p>같은 클램프 로직이 도메인마다 복붙되고 있어 한 곳으로 모았습니다.
 * 값이 없거나 0 이하면 기본값을, 상한을 넘으면 상한을 씁니다.
 */
public final class Paging {

    private Paging() {
    }

    public static int normalize(Integer requested, int defaultSize, int maxSize) {
        if (requested == null || requested <= 0) {
            return defaultSize;
        }
        return Math.min(requested, maxSize);
    }
}
