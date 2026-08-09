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

/** 잡담 글 해시태그. */
@Entity
@Table(name = "post_hashtag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostHashtag {

    public static final int TAG_MAX_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;

    @Column(name = "tag", nullable = false, length = TAG_MAX_LENGTH)
    private String tag;

    public PostHashtag(Post post, String tag) {
        this.post = post;
        this.tag = tag;
    }
}
