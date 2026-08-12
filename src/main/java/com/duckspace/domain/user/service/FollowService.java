package com.duckspace.domain.user.service;

import com.duckspace.domain.user.dto.response.FollowUserResponse;
import com.duckspace.domain.user.entity.Follow;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.exception.UserErrorCode;
import com.duckspace.domain.user.repository.FollowRepository;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new BusinessException(UserErrorCode.SELF_FOLLOW_NOT_ALLOWED);
        }
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new BusinessException(UserErrorCode.ALREADY_FOLLOWING);
        }

        User follower = getUser(followerId);
        User following = getUser(followingId);
        try {
            followRepository.saveAndFlush(Follow.of(follower,following));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(UserErrorCode.ALREADY_FOLLOWING);
        }
    }

    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        if (!followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new BusinessException(UserErrorCode.NOT_FOLLOWING);
        }
        followRepository.deleteByFollowerIdAndFollowingId(followerId, followingId);
    }

    public List<FollowUserResponse> getFollowers(Long userId, Long cursor, Integer size) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        return followRepository.findFollowersByFollowingId(userId, cursor, pageable(size)).stream()
                .map(follow -> FollowUserResponse.from(follow.getFollower()))
                .toList();
    }

    public List<FollowUserResponse> getFollowing(Long userId, Long cursor, Integer size) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        return followRepository.findFollowingByFollowerId(userId, cursor, pageable(size)).stream()
                .map(follow -> FollowUserResponse.from(follow.getFollowing()))
                .toList();
    }

    public long countFollowers(Long userId) {
        return followRepository.countByFollowingId(userId);
    }

    public long countFollowing(Long userId) {
        return followRepository.countByFollowerId(userId);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private Pageable pageable(Integer size) {
        return PageRequest.of(0, normalizeSize(size));
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
