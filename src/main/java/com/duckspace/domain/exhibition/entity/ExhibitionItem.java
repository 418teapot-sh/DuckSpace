package com.duckspace.domain.exhibition.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExhibitionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exhibition_id", nullable = false)
    private Exhibition exhibition;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String imageUrl;

    @Lob
    private String description;

    @Column(nullable = false)
    private int sortOrder;

    @Builder
    private ExhibitionItem(String name, String imageUrl, String description, int sortOrder) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    void assignTo(Exhibition exhibition) {
        this.exhibition = exhibition;
    }
}