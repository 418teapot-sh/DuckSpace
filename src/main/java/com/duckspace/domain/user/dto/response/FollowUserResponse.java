package com.duckspace.domain.user.dto.response;

import com.duckspace.domain.user.entity.User;

public record FollowUserResponse(
        Long userId,
        String nickname,
        String profileImageUrl // 새로 추가된 필드
) {

    public static FollowUserResponse from(User user) {
        return new FollowUserResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl() // User 엔티티에서 이미지 URL 가져오기
        );
    }
}