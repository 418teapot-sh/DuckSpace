package com.duckspace.global.support;

import java.util.List;
import java.util.function.Function;

/**
 * 페이지 크기·조회 개수 보정과 커서 페이지 절단.
 *
 * <p>같은 로직이 도메인마다 복붙되고 있어 한 곳으로 모았습니다.
 */
public final class Paging {

    private Paging() {
    }

    /** 값이 없거나 0 이하면 기본값을, 상한을 넘으면 상한을 씁니다. */
    public static int normalize(Integer requested, int defaultSize, int maxSize) {
        if (requested == null || requested <= 0) {
            return defaultSize;
        }
        return Math.min(requested, maxSize);
    }

    /**
     * "한 개 더 가져와서 다음 페이지 유무를 아는" 커서 페이징의 뒷정리.
     *
     * @param found  {@code pageSize + 1} 개를 요청해 받은 결과
     * @param cursorOf 마지막 항목에서 다음 커서를 뽑는 함수 (보통 {@code Entity::getId})
     */
    public static <T> Slice<T> slice(List<T> found, int pageSize, Function<T, Long> cursorOf) {
        boolean hasNext = found.size() > pageSize;
        List<T> page = hasNext ? found.subList(0, pageSize) : found;
        Long nextCursor = hasNext ? cursorOf.apply(page.get(page.size() - 1)) : null;
        return new Slice<>(page, nextCursor, hasNext);
    }

    /** 한 페이지 분량과 다음 커서. */
    public record Slice<T>(List<T> page, Long nextCursor, boolean hasNext) {
    }
}
