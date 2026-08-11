package com.duckspace.domain.exhibition.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"exhibition_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExhibitionLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exhibition_id", nullable = false)
    private Exhibition exhibition;

    /** User는 다른 담당자의 도메인이라 연관관계 대신 id만 저장합니다. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Builder
    private ExhibitionLike(Exhibition exhibition, Long userId) {
        this.exhibition = exhibition;
        this.userId = userId;
    }
}