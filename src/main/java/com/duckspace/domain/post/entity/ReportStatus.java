package com.duckspace.domain.post.entity;

public enum ReportStatus {
    /** 접수됨, 아직 처리 전. */
    PENDING,
    /** 운영진이 확인함. */
    REVIEWED,
    /** 확인 후 실제 조치(삭제/정지 등)함. */
    ACTIONED,
}
