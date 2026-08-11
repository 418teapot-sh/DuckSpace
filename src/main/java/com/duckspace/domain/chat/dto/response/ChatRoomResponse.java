package com.duckspace.domain.chat.dto.response;

import com.duckspace.domain.chat.entity.ChatMessage;
import com.duckspace.domain.chat.entity.ChatRoom;

import java.time.LocalDateTime;

/**
 * 채팅방 목록 항목.
 *
 * @param hasUnread 안 읽은 메시지가 있는지. 정확한 개수는 방마다 기준점이 달라 쿼리가 늘어나므로,
 *                  목록에서는 여부만 내려줍니다. (배지 표시에는 이걸로 충분합니다)
 */
public record ChatRoomResponse(
        Long roomId,
        Long partnerId,
        String partnerNickname,
        String lastMessage,
        LocalDateTime lastMessageAt,
        boolean hasUnread
) {

    public static ChatRoomResponse of(ChatRoom room, Long viewerId, String partnerNickname, ChatMessage lastMessage) {
        boolean unread = lastMessage != null
                && !lastMessage.getSenderId().equals(viewerId)
                && isNewerThanRead(room, viewerId, lastMessage.getId());

        return new ChatRoomResponse(
                room.getId(),
                room.partnerOf(viewerId),
                partnerNickname,
                lastMessage == null ? null : lastMessage.getContent(),
                lastMessage == null ? null : lastMessage.getCreatedAt(),
                unread
        );
    }

    private static boolean isNewerThanRead(ChatRoom room, Long viewerId, Long messageId) {
        Long lastRead = room.lastReadMessageIdOf(viewerId);
        return lastRead == null || messageId > lastRead;
    }
}
