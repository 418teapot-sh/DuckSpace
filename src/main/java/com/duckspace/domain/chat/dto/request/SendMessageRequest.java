package com.duckspace.domain.chat.dto.request;

import com.duckspace.domain.chat.entity.ChatMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank @Size(max = ChatMessage.MAX_CONTENT_LENGTH) String content
) {
}
