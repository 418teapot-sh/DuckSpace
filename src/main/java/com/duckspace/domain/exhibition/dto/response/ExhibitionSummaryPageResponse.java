package com.duckspace.domain.exhibition.dto.response;

import java.util.List;

/**
 * 장식장 피드 더보기 응답.
 *
 * <p><b>끝인지 아닌지는 {@code hasNext} 로만 판단하세요.</b> {@code items.length} 가 요청한
 * {@code size} 보다 작아도 마지막 페이지가 아닐 수 있습니다.
 *
 * <p>id 를 먼저 뽑고 그 id 로 본문을 가져오는 2단 조회라, 두 조회 사이에 장식장이 삭제되면
 * 그만큼 {@code items} 가 짧아집니다. {@code hasNext} 와 {@code nextCursor} 는 id 조회
 * 기준이라 그대로 정확합니다 — 짧아진 페이지를 받아도 {@code nextCursor} 로 계속 넘기면 됩니다.
 *
 * <p>{@code items.length < size} 를 종료 조건으로 쓰면 <b>남은 데이터를 두고 멈춥니다.</b>
 *
 * @param items      이 페이지의 장식장들. 요청한 {@code size} 보다 짧을 수 있습니다
 * @param nextCursor 다음 요청의 {@code cursor} 로 그대로 넣으세요. 더 없으면 null 입니다
 * @param hasNext    다음 페이지 존재 여부. <b>종료 판단은 이 값으로 하세요</b>
 */
public record ExhibitionSummaryPageResponse(
        List<ExhibitionSummaryResponse> items,
        Long nextCursor,
        boolean hasNext
) {
}
