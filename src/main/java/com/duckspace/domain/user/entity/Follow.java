package com.duckspace.domain.user.entity;

import com.duckspace.domain.user.exception.UserErrorCode;
import com.duckspace.global.entity.BaseTimeEntity;
import com.duckspace.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/** 팔로우 관계 한 건. follower가 following을 팔로우합니다. */
@Entity
@Table(name = "follow",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_follow_follower_following",
                columnNames = {"follower_id", "following_id"}),
        // 팔로워 목록·팔로워 수는 following_id로 조회해서 유니크 제약(follower_id, following_id)을 못 탑니다.
        indexes = @Index(name = "idx_follow_following", columnList = "following_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false, updatable = false)
    private User follower;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "following_id", nullable = false, updatable = false)
    private User following;

    private Follow(User follower, User following) {
        this.follower = follower;
        this.following = following;
    }

    public static Follow of(User follower, User following) {
        if (Objects.equals(follower.getId(), following.getId())) {
            throw new BusinessException(UserErrorCode.SELF_FOLLOW_NOT_ALLOWED);
        }
        return new Follow(follower, following);
    }
}