package com.duckspace.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 교환 글의 부가 정보. {@link Post}와 1:1이며 postId를 PK 겸 FK로 공유합니다.
 */
@Entity
@Table(name = "exchange_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeDetail {

    public static final int EXTRA_CONDITION_MAX_LENGTH = 200;

    @Id
    @Column(name = "post_id")
    private Long postId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "post_id")
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private ExchangeMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExchangeStatus status;

    @Column(name = "extra_condition", length = EXTRA_CONDITION_MAX_LENGTH)
    private String extraCondition;

    public ExchangeDetail(Post post, ExchangeMethod method, String extraCondition) {
        this.post = post;
        this.method = method;
        this.status = ExchangeStatus.OPEN;
        this.extraCondition = extraCondition;
    }

    public void complete() {
        this.status = ExchangeStatus.COMPLETED;
    }

    public boolean isCompleted() {
        return this.status == ExchangeStatus.COMPLETED;
    }
}
