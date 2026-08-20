package com.duckspace.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 잡담 글 첨부 사진. 최대 4장. */
@Entity
@Table(name = "post_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage {

    public static final int MAX_COUNT = 4;
    public static final int IMAGE_URL_MAX_LENGTH = 255;

    /**
     * 목록 카드에 쓰는 <b>대표 이미지</b>의 {@code sortOrder}.
     *
     * <p>노출 순서가 {@code sortOrder} 오름차순이라 맨 앞이 대표입니다. 목록 배치 조회
     * ({@code PostImageRepository#findThumbnails})가 이 값으로 한 장만 골라옵니다.
     */
    public static final int THUMBNAIL_SORT_ORDER = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;

    @Column(name = "image_url", nullable = false, length = IMAGE_URL_MAX_LENGTH)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public PostImage(Post post, String imageUrl, int sortOrder) {
        this.post = post;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }
}
