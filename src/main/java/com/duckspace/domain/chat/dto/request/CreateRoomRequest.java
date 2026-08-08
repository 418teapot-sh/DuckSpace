package com.duckspace.domain.chat.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreateRoomRequest(
        @NotNull Long partnerId
) {
}
