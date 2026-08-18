package com.duckspace.domain.popup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "popup_like",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_popup_like_popup_user",
                columnNames = {"popup_id", "user_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PopupLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "popup_id", nullable = false, updatable = false)
    private Long popupId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    public PopupLike(Long popupId, Long userId) {
        this.popupId = popupId;
        this.userId = userId;
    }
}
