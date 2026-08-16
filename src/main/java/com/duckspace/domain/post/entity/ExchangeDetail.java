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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 교환 글의 부가 정보. {@link Post}와 1:1이며 postId를 PK 겸 FK로 공유합니다.
 */
@Entity
@Table(name = "exchange_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeDetail {

    public static final int EXTRA_CONDITION_MAX_LENGTH = 200;
    public static final int PREFERRED_TEXT_MAX_LENGTH = 50;

    @Id
    @Column(name = "post_id")
    private Long postId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "post_id")
    private Post post;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false)
    private ExchangeStatus status;

    @Column(name = "extra_condition", length = EXTRA_CONDITION_MAX_LENGTH)
    private String extraCondition;

    /** 교환하고 싶은 팝업 이름. 선택 입력. */
    @Column(name = "preferred_popup_name", length = PREFERRED_TEXT_MAX_LENGTH)
    private String preferredPopupName;

    /** 선호 날짜. 자유 텍스트(예: "260809"). 선택 입력. */
    @Column(name = "preferred_date", length = PREFERRED_TEXT_MAX_LENGTH)
    private String preferredDate;

    /** 선호 시간. 자유 텍스트(예: "12시부터14시까지"). 선택 입력. */
    @Column(name = "preferred_time", length = PREFERRED_TEXT_MAX_LENGTH)
    private String preferredTime;

    public ExchangeDetail(Post post, String extraCondition,
                           String preferredPopupName, String preferredDate, String preferredTime) {
        this.post = post;
        this.status = ExchangeStatus.OPEN;
        this.extraCondition = extraCondition;
        this.preferredPopupName = preferredPopupName;
        this.preferredDate = preferredDate;
        this.preferredTime = preferredTime;
    }

    public void complete() {
        this.status = ExchangeStatus.COMPLETED;
    }

    public boolean isCompleted() {
        return this.status == ExchangeStatus.COMPLETED;
    }
}
