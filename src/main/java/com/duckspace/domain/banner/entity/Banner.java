package com.duckspace.domain.banner.entity;

import com.duckspace.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
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

    /** 배너 클릭 시 이동할 팝업 id */
    @Column(nullable = false)
    private Long popupId;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private int sortOrder;

    @Builder
    private Banner(String imageUrl, String title, Long popupId,
                    LocalDateTime startAt, LocalDateTime endAt, int sortOrder) {
        this.imageUrl = imageUrl;
        this.title = title;
        this.popupId = popupId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.sortOrder = sortOrder;
    }

    public void update(String imageUrl, String title, Long popupId,
                        LocalDateTime startAt, LocalDateTime endAt, int sortOrder) {
        this.imageUrl = imageUrl;
        this.title = title;
        this.popupId = popupId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.sortOrder = sortOrder;
    }
}