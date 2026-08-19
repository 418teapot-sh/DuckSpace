package com.duckspace.domain.user.dto.response;

import com.duckspace.domain.user.entity.User;

public record UserSearchResponse(
        Long userId,
        String nickname,
        String profileImageUrl
) {

    public static UserSearchResponse from(User user) {
        return new UserSearchResponse(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }
}
