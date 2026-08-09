package com.duckspace.domain.post.entity;

import com.duckspace.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 게시글/댓글 신고. targetType + targetId로 대상을 가리킵니다 (신고 대상 테이블에 FK를 걸지 않아 둘을 하나로 다룰 수 있음). */
@Entity
@Table(name = "report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {

    public static final int REASON_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @Column(name = "reason", length = REASON_MAX_LENGTH)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus status;

    public Report(Long reporterId, ReportTargetType targetType, Long targetId, String reason) {
        this.reporterId = reporterId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.status = ReportStatus.PENDING;
    }
}