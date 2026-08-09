package com.duckspace.domain.post.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 게시글 댓글. 대댓글은 서비스 레이어에서 1단계로만 제한합니다
 * (parent 자체가 이미 대댓글이면 그 밑에 또 답글을 달 수 없음).
 */
@Entity
@Table(name = "comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {

    public static final int CONTENT_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** 부모 댓글이 지워지면 답글도 DB 레벨에서 같이 지워집니다(ON DELETE CASCADE). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Comment parent;

    @Column(name = "content", nullable = false, length = CONTENT_MAX_LENGTH)
    private String content;

    @Column(name = "is_secret", nullable = false)
    private boolean secret;

    public Comment(Post post, Long userId, Comment parent, String content, boolean secret) {
        this.post = post;
        this.userId = userId;
        this.parent = parent;
        this.content = content;
        this.secret = secret;
    }

    public boolean isReply() {
        return parent != null;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** 비밀댓글은 작성자 본인 또는 게시글 주인만 볼 수 있습니다. */
    public boolean isVisibleTo(Long viewerId) {
        if (!secret) {
            return true;
        }
        return isOwnedBy(viewerId) || post.isOwnedBy(viewerId);
    }
}
