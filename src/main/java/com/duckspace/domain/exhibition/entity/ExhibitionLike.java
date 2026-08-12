package com.duckspace.domain.exhibition.entity;

import com.duckspace.global.entity.BaseTimeEntity;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장식장 좋아요. 한 사람이 같은 장식장에 좋아요를 두 번 누를 수 없습니다.
 */
@Entity
@Table(
        name = "exhibition_like",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exhibition_like",
                columnNames = {"exhibition_id", "user_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExhibitionLike extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exhibition_id", nullable = false, updatable = false)
    private Exhibition exhibition;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    public ExhibitionLike(Exhibition exhibition, Long userId) {
        this.exhibition = exhibition;
        this.userId = userId;
    }
}
