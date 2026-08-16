package com.duckspace.domain.post.entity;

import com.duckspace.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 덕톡라운지 게시글. 잡담(CASUAL)과 교환(EXCHANGE)이 이 테이블을 공유합니다.
 *
 * <p>잡담과 교환은 부가 정보 모양이 완전히 달라서(교환은 {@link ExchangeDetail}·{@link TradeItem}으로 구조화)
 * 공통 필드만 여기 두고 나머지는 보드 타입별 테이블로 분리했습니다.
 */
@Entity
@Table(name = "post", indexes = {
        @Index(name = "idx_post_feed", columnList = "board_type, deleted_at, id"),
        // authorId 필터(마이페이지)용. board_type/deleted_at까지 넣어 커버링에 가깝게 만듭니다.
        @Index(name = "idx_post_author_feed", columnList = "user_id, board_type, deleted_at, id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseTimeEntity {

    /** 잡담 글 최대 길이. */
    public static final int CASUAL_CONTENT_MAX_LENGTH = 500;
    /** 교환 글 최대 길이. */
    public static final int EXCHANGE_CONTENT_MAX_LENGTH = 200;
    public static final int TITLE_MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "board_type", nullable = false, updatable = false)
    private BoardType boardType;

    /** 교환 글만 사용합니다. 잡담 글은 null. */
    @Column(name = "title", length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "content", length = CASUAL_CONTENT_MAX_LENGTH)
    private String content;

    /** null이 아니면 삭제된 글. 댓글/좋아요 등 연관 데이터는 지우지 않고 그대로 두되, 조회 경로에서만 숨깁니다. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Post(Long userId, BoardType boardType, String title, String content) {
        this.userId = userId;
        this.boardType = boardType;
        this.title = title;
        this.content = content;
    }

    public static Post createCasual(Long userId, String content) {
        return new Post(userId, BoardType.CASUAL, null, content);
    }

    public static Post createExchange(Long userId, String title, String content) {
        return new Post(userId, BoardType.EXCHANGE, title, content);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** 잡담 글에서만 씁니다. 호출 전 boardType이 CASUAL인지 서비스 레이어에서 확인하세요. */
    public void updateContent(String content) {
        this.content = content;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
