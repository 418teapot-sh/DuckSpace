package com.duckspace.domain.post.entity;

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
        name = "post_like",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_post_like_post_user",
                columnNames = {"post_id", "user_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    public PostLike(Long postId, Long userId) {
        this.postId = postId;
        this.userId = userId;
    }
}
