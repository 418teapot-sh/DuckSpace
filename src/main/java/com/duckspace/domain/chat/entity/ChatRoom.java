package com.duckspace.domain.chat.entity;

import com.duckspace.global.entity.BaseTimeEntity;
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

import java.util.Objects;

/**
 * 두 사용자 사이의 1:1 채팅방.
 *
 * <p>교환 게시판이 아직 없어 <b>게시글이 아니라 사용자 쌍</b>을 기준으로 방을 만듭니다.
 * 나중에 교환글과 연결해야 하면 nullable 컬럼을 추가하면 됩니다.
 *
 * <p>같은 두 사람에게 방이 두 개 생기지 않도록 <b>id가 작은 쪽을 항상 userA</b>로 정렬해 저장하고,
 * (userA, userB)에 유니크 제약을 겁니다.
 */
@Entity
@Table(
        name = "chat_room",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_room_participants",
                columnNames = {"user_a_id", "user_b_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    /** 두 참여자 중 id가 작은 쪽. */
    @Column(name = "user_a_id", nullable = false, updatable = false)
    private Long userAId;

    /** 두 참여자 중 id가 큰 쪽. */
    @Column(name = "user_b_id", nullable = false, updatable = false)
    private Long userBId;

    /** userA가 마지막으로 읽은 메시지 id. 안 읽음 표시에 사용합니다. */
    @Column(name = "user_a_last_read_message_id")
    private Long userALastReadMessageId;

    @Column(name = "user_b_last_read_message_id")
    private Long userBLastReadMessageId;

    private ChatRoom(Long userAId, Long userBId) {
        this.userAId = userAId;
        this.userBId = userBId;
    }

    /** 순서에 상관없이 항상 같은 방이 되도록 두 id를 정렬해 생성합니다. */
    public static ChatRoom between(Long userId1, Long userId2) {
        return new ChatRoom(Math.min(userId1, userId2), Math.max(userId1, userId2));
    }

    public boolean hasParticipant(Long userId) {
        return Objects.equals(userAId, userId) || Objects.equals(userBId, userId);
    }

    /** 주어진 참여자의 상대방 id. */
    public Long partnerOf(Long userId) {
        return Objects.equals(userAId, userId) ? userBId : userAId;
    }

    public Long lastReadMessageIdOf(Long userId) {
        return Objects.equals(userAId, userId) ? userALastReadMessageId : userBLastReadMessageId;
    }

    /** 이미 더 뒤까지 읽은 상태라면 값을 되돌리지 않습니다. */
    public void markRead(Long userId, Long messageId) {
        if (messageId == null) {
            return;
        }
        Long current = lastReadMessageIdOf(userId);
        if (current != null && current >= messageId) {
            return;
        }
        if (Objects.equals(userAId, userId)) {
            this.userALastReadMessageId = messageId;
        } else {
            this.userBLastReadMessageId = messageId;
        }
    }
}
