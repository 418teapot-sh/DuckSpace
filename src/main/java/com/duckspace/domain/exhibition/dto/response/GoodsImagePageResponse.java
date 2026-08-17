package com.duckspace.domain.exhibition.dto.response;

import java.util.List;

/**
 * 보관함 목록 한 페이지.
 *
 * @param nextCursor 다음 요청의 {@code cursor} 에 넣을 값. 더 없으면 null
 */
public record GoodsImagePageResponse(
        List<GoodsImageResponse> images,
        Long nextCursor,
        boolean hasNext
) {
}
