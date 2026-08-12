package com.duckspace.domain.exhibition.service;

/**
 * 굿즈 사진이 접수되었음을 알리는 이벤트.
 *
 * <p>이벤트를 거치는 이유는 <b>순서</b> 때문입니다. 저장 트랜잭션이 커밋되기 전에 백그라운드
 * 스레드가 아이템을 조회하면 아직 없어서 처리에 실패합니다. 커밋 이후에만 처리가 시작되도록
 * {@code AFTER_COMMIT} 으로 받습니다.
 *
 * @param itemId    상태가 PENDING 으로 저장된 아이템
 * @param imageData 원본 바이트
 * @param fileName  원본 파일명
 */
public record ItemImageUploadedEvent(Long itemId, byte[] imageData, String fileName) {
}
