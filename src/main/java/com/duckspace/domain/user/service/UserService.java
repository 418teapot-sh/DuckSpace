package com.duckspace.domain.user.service;

import com.duckspace.domain.user.dto.request.UpdateProfileRequest;
import com.duckspace.domain.user.dto.response.UserProfileResponse;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.exception.UserErrorCode;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final FollowService followService;

    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        long followerCount = followService.countFollowers(userId);
        long followingCount = followService.countFollowing(userId);
        return UserProfileResponse.of(user, followerCount, followingCount);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        user.updateProfile(request.nickname(), request.profileImageUrl());

        long followerCount = followService.countFollowers(userId);
        long followingCount = followService.countFollowing(userId);
        return UserProfileResponse.of(user, followerCount, followingCount);
    }
}
