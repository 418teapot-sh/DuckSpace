package com.duckspace.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 교환 게시글에 대한 신청 한 건. 게시글 하나에 여러 명이 신청할 수 있고,
 * 게시글 작성자가 그중 하나를 골라 수락({@link #accept()})한 뒤 완료({@link #complete()})합니다.
 * 상태 전이가 유효한지(예: APPLIED 상태에서만 accept 가능)는 서비스 레이어에서 확인합니다 — 이 클래스는 상태만 바꿉니다.
 */
@Entity
@Table(name = "exchange_application", indexes = {
        @Index(name = "idx_exchange_application_post", columnList = "post_id"),
        @Index(name = "idx_exchange_application_applicant", columnList = "applicant_user_id"),
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeApplication {

    public static final int OFFERED_ITEM_NAME_MAX_LENGTH = TradeItem.ITEM_NAME_MAX_LENGTH;
    public static final int OFFERED_IMAGE_URL_MAX_LENGTH = TradeItem.IMAGE_URL_MAX_LENGTH;
    public static final int MESSAGE_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;

    @Column(name = "applicant_user_id", nullable = false, updatable = false)
    private Long applicantUserId;

    @Column(name = "offered_item_name", nullable = false, length = OFFERED_ITEM_NAME_MAX_LENGTH)
    private String offeredItemName;

    @Column(name = "offered_image_url", length = OFFERED_IMAGE_URL_MAX_LENGTH)
    private String offeredImageUrl;

    @Column(name = "message", length = MESSAGE_MAX_LENGTH)
    private String message;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false)
    private ExchangeApplicationStatus status;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public ExchangeApplication(Long postId, Long applicantUserId, String offeredItemName,
                                String offeredImageUrl, String message) {
        this.postId = postId;
        this.applicantUserId = applicantUserId;
        this.offeredItemName = offeredItemName;
        this.offeredImageUrl = offeredImageUrl;
        this.message = message;
        this.status = ExchangeApplicationStatus.APPLIED;
        this.appliedAt = LocalDateTime.now();
    }

    public boolean isOwnedByApplicant(Long userId) {
        return this.applicantUserId.equals(userId);
    }

    public boolean isApplied() {
        return this.status == ExchangeApplicationStatus.APPLIED;
    }

    public boolean isAccepted() {
        return this.status == ExchangeApplicationStatus.ACCEPTED;
    }

    public void accept() {
        this.status = ExchangeApplicationStatus.ACCEPTED;
        this.respondedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = ExchangeApplicationStatus.REJECTED;
        this.respondedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = ExchangeApplicationStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = ExchangeApplicationStatus.CANCELLED;
    }
}
