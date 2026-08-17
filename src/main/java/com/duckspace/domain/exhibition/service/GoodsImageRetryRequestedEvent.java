package com.duckspace.domain.exhibition.service;

/**
 * 실패한 보관함 사진을 다시 처리해달라는 요청.
 *
 * <p>{@link ItemImageRetryRequestedEvent} 와 같은 이유로 바이트 대신 주소만 담습니다 —
 * 원본 다운로드(S3 왕복)는 요청 스레드가 아니라 백그라운드에서 합니다.
 *
 * @param imageId        다시 처리할 사진 (PENDING 으로 되돌아간 상태)
 * @param userId         저장 경로를 만드는 데 씁니다
 * @param sourceImageUrl 실패했을 때 남겨둔 원본 주소
 */
public record GoodsImageRetryRequestedEvent(Long imageId, Long userId, String sourceImageUrl) {
}
