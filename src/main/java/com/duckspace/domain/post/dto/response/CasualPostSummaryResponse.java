package com.duckspace.domain.post.dto.response;

import java.time.LocalDateTime;

/**
 * 잡담 목록 카드 한 장.
 *
 * @param thumbnailUrl 첨부 사진 중 <b>대표 1장</b>({@code sortOrder = 0}). 사진이 없으면 null 입니다.
 *                     카드가 이미지를 한 장만 그려서 나머지는 싣지 않습니다 — 전체 목록은 상세
 *                     응답의 {@code imageUrls} 를 보세요.
 */
public record CasualPostSummaryResponse(
        Long id,
        String content,
        Long authorId,
        String authorNickname,
        LocalDateTime createdAt,
        long likeCount,
        long commentCount,
        String thumbnailUrl
) {
}
