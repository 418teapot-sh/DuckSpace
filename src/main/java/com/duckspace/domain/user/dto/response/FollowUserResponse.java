package com.duckspace.domain.user.dto.response;

import com.duckspace.domain.user.entity.User;

public record FollowUserResponse(
        Long userId,
        String nickname
) {

    public static FollowUserResponse from(User user) {
        return new FollowUserResponse(user.getId(), user.getNickname());
    }
}
