package com.duckspace.domain.exhibition.dto.response;

import java.util.List;

/**
 * 그리드 더보기 응답.
 *
 * @param nextCursor 다음 요청의 {@code cursor} 로 그대로 넣으세요. 더 없으면 null 입니다.
 */
public record ExhibitionItemPageResponse(
        List<ExhibitionItemResponse> items,
        Long nextCursor,
        boolean hasNext
) {
}
