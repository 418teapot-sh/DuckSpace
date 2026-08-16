package com.duckspace.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 30) String nickname,
        @Size(max = 500) String profileImageUrl
) {
}
