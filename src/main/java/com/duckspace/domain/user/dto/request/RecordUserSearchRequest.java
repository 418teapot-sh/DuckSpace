package com.duckspace.domain.user.dto.request;

import jakarta.validation.constraints.NotNull;

public record RecordUserSearchRequest(
        @NotNull Long targetUserId
) {
}
