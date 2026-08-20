package com.duckspace.domain.post.dto.response;

import java.time.LocalDateTime;

/**
 * 잡담 목록 카드 한 장.
 *
 * <p>카드를 그리는 데 필요한 것을 <b>이 응답 하나로 다 담습니다.</b> 예전에는 사진 · 작성자
 * 프로필 · 좋아요 여부가 빠져 있어서, 클라이언트가 카드마다 상세 API 와 유저 API 를 따로 불러
 * 메우고 있었습니다(글 12개면 요청 24~36번).
 *
 * @param thumbnailUrl          첨부 사진 중 <b>대표 1장</b>({@code sortOrder = 0}). 사진이 없으면 null.
 *                              카드가 이미지를 한 장만 그려서 나머지는 싣지 않습니다 — 전체 목록은
 *                              상세 응답의 {@code imageUrls} 를 보세요
 * @param authorProfileImageUrl 작성자 프로필 사진. 설정 안 했으면 null
 * @param liked                 <b>보는 사람</b>이 이 글에 좋아요를 눌렀는지. 이게 없던 동안에는
 *                              새로고침할 때마다 눌러둔 좋아요가 풀린 것처럼 보였습니다
 */
public record CasualPostSummaryResponse(
        Long id,
        String content,
        Long authorId,
        String authorNickname,
        String authorProfileImageUrl,
        LocalDateTime createdAt,
        long likeCount,
        long commentCount,
        boolean liked,
        String thumbnailUrl
) {
}
