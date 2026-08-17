package com.duckspace.domain.exhibition.service;

/**
 * 보관함으로 굿즈 사진이 접수되었음을 알리는 이벤트.
 *
 * <p>{@link ItemImageUploadedEvent} 와 같은 이유로 커밋 이후({@code AFTER_COMMIT})에만
 * 처리가 시작됩니다. 굿즈와 달리 장식장이 없으므로 저장 경로는 {@code userId} 로 만듭니다.
 *
 * @param imageId   상태가 PENDING 으로 저장된 보관함 사진
 * @param userId    소유자. 저장 경로({@code images/{userId}/...})에 씁니다
 * @param imageData 원본 바이트
 * @param fileName  원본 파일명
 */
public record GoodsImageUploadedEvent(Long imageId, Long userId, byte[] imageData, String fileName) {
}
