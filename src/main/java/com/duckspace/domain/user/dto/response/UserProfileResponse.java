package com.duckspace.domain.user.dto.response;

import com.duckspace.domain.user.entity.User;

public record UserProfileResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        // 팔로우 및 팔로잉 수 세기
        long followerCount,
        long followingCount
) {

    public static UserProfileResponse of(User user, long followerCount, long followingCount) {
        return new UserProfileResponse(
                user.getId(), user.getNickname(), user.getProfileImageUrl(), followerCount, followingCount);
    }
}
