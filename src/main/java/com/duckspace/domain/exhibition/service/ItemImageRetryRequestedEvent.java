package com.duckspace.domain.exhibition.service;

/**
 * 실패한 굿즈를 다시 처리해달라는 요청.
 *
 * <p>업로드 때와 달리 <b>이미지 바이트를 담지 않습니다.</b> 원본은 실패 시 저장소에 남겨둔 것이
 * 전부이고, 그걸 다시 읽는 건 S3 왕복이라 요청 스레드에서 하면 안 됩니다. 주소만 넘기고
 * 백그라운드에서 내려받습니다.
 *
 * @param itemId         다시 처리할 아이템 (상태가 PENDING 으로 되돌아간 상태)
 * @param exhibitionId   저장 경로를 만드는 데 씁니다
 * @param sourceImageUrl 실패했을 때 남겨둔 원본 주소
 */
public record ItemImageRetryRequestedEvent(Long itemId, Long exhibitionId, String sourceImageUrl) {
}
