package com.duckspace.domain.user.service;

import com.duckspace.domain.user.dto.response.FollowUserResponse;
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
    private final FollowWriter followWriter;

    /**
     * 팔로우. 이미 팔로우한 상태여도, 동시에 두 번 눌려도 결과가 같습니다(멱등).
     *
     * <p>INSERT는 {@link FollowWriter}가 별도 트랜잭션에서 수행합니다.
     * 같은 트랜잭션에서 유니크 제약 위반을 잡고 넘어가면 rollback-only 상태로 남아
     * 커밋 시점에 {@code UnexpectedRollbackException}이 날 수 있기 때문입니다.
     */
    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new BusinessException(UserErrorCode.SELF_FOLLOW_NOT_ALLOWED);
        }
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            return;
        }

        User follower = getUser(followerId);
        User following = getUser(followingId);
        try {
            followWriter.insert(follower, following);
        } catch (DataIntegrityViolationException e) {
            // 유니크 제약 위반(동시에 두 번 눌림)이면 이미 팔로우된 상태이므로 성공으로 봅니다.
            // FK 제약 위반(getUser()와 insert() 사이에 상대 계정이 삭제됨)이면 기록되지 않았으므로 예외를 던집니다.
            if (!followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
                throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
            }
        }
    }

    /** 언팔로우. 팔로우한 적이 없어도 성공으로 처리합니다(멱등). */
    @Transactional
    public void unfollow(Long followerId, Long followingId) {
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
