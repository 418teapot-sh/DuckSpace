package com.duckspace.domain.exhibition.entity;

/**
 * 아이템 이미지 처리 상태.
 *
 * <p>이미지 업로드는 배경 제거·S3 업로드를 거쳐야 해서 수 초가 걸립니다. 업로드 요청은 즉시 응답하고
 * 처리는 뒤에서 돌리기 위해 상태를 둡니다. 프론트는 {@code READY} 가 될 때까지 폴링합니다.
 *
 * <p>파이프라인이 붙기 전(이미지 URL 을 직접 받는 단계)에는 항상 {@code READY} 로 저장됩니다.
 */
public enum ItemStatus {
    /** 업로드 접수됨. 배경 제거·업로드 진행 중. */
    PENDING,
    /** 처리 완료. 화면에 표시 가능. */
    READY,
    /** 처리 실패. 재시도하거나 삭제해야 합니다. */
    FAILED
}
