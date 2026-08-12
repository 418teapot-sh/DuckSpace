package com.duckspace.domain.chat.dto.response;

import com.duckspace.domain.chat.entity.ChatMessage;

import java.time.LocalDateTime;

/**
 * @param mine 내가 보낸 메시지인지. 프론트에서 말풍선 좌우를 나누는 데 씁니다.
 */
public record ChatMessageResponse(
        Long messageId,
        Long senderId,
        boolean mine,
        String content,
        LocalDateTime createdAt
) {

    public static ChatMessageResponse of(ChatMessage message, Long viewerId) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSenderId(),
                message.getSenderId().equals(viewerId),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
