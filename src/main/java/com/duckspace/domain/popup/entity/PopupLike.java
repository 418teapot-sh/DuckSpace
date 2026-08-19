package com.duckspace.domain.popup.entity;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "popup_id", nullable = false, updatable = false)
    private Popup popup;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    public PopupLike(Popup popup, Long userId) {
        this.popup = popup;
        this.userId = userId;
    }
}
