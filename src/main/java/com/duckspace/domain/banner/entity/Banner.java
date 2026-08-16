package com.duckspace.domain.banner.entity;

import com.duckspace.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "banner", indexes = @Index(name = "idx_banner_period", columnList = "start_at, end_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Banner extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String imageUrl;

    @Column(nullable = false)
    private String title;

    @Lob
    private String description;

    /** OpenAI가 생성한 홍보 문구 요약. 등록/수정 시점에 서버가 채워 넣는다. */
    @Lob
    private String aiSummary;

    /** 배너 클릭 시 이동할 팝업 id */
    @Column(nullable = false)
    private Long popupId;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private int sortOrder;

    /** 기간 안이어도 이걸 끄면 즉시 노출을 내릴 수 있습니다(긴급 중단용). */
    @Column(nullable = false)
    private boolean active;

    @Builder
    private Banner(String imageUrl, String title, String description, String aiSummary, Long popupId,
                    LocalDateTime startAt, LocalDateTime endAt, int sortOrder, Boolean active) {
        this.imageUrl = imageUrl;
        this.title = title;
        this.description = description;
        this.aiSummary = aiSummary;
        this.popupId = popupId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.sortOrder = sortOrder;
        this.active = active == null || active;
    }

    /** active를 생략(null)하면 지금 켜져 있는지 꺼져 있는지를 그대로 유지합니다. */
    public void update(String imageUrl, String title, String description, String aiSummary, Long popupId,
                        LocalDateTime startAt, LocalDateTime endAt, int sortOrder, Boolean active) {
        this.imageUrl = imageUrl;
        this.title = title;
        this.description = description;
        this.aiSummary = aiSummary;
        this.popupId = popupId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.sortOrder = sortOrder;
        this.active = active == null ? this.active : active;
    }
}