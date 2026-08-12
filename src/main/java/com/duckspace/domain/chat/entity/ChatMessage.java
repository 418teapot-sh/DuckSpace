package com.duckspace.domain.chat.entity;

import com.duckspace.global.entity.BaseTimeEntity;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅 메시지.
 *
 * <p>폴링 조회가 {@code where room_id = ? and id > ?} 형태라 (room_id, id) 복합 인덱스를 둡니다.
 * id가 단조 증가하므로 시각 대신 id를 커서로 씁니다.
 */
@Entity
@Table(
        name = "chat_message",
        indexes = @Index(name = "idx_chat_message_room_id", columnList = "room_id, id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    /** 메시지 본문 최대 길이. DTO의 @Size 와 반드시 같은 값을 유지하세요. */
    public static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, updatable = false)
    private ChatRoom room;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private Long senderId;

    @Column(name = "content", nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    public ChatMessage(ChatRoom room, Long senderId, String content) {
        this.room = room;
        this.senderId = senderId;
        this.content = content;
    }
}
