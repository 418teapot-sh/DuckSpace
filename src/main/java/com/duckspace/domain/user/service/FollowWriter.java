package com.duckspace.domain.user.service;

import com.duckspace.domain.user.entity.Follow;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팔로우 INSERT 만 <b>별도 트랜잭션</b>에서 수행합니다.
 *
 * <p>같은 트랜잭션 안에서 유니크 제약 위반을 잡고 넘어가면, 그 트랜잭션은 이미 rollback-only 로
 * 표시되어 있어 커밋 시점에 {@code UnexpectedRollbackException} 이 납니다.
 *
 * <p><b>별도 빈인 이유:</b> 같은 빈 안에서 호출하면 프록시를 타지 않아
 * {@code REQUIRES_NEW} 가 적용되지 않습니다.
 */
@Component
@RequiredArgsConstructor
class FollowWriter {

    private final FollowRepository followRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(User follower, User following) {
        followRepository.saveAndFlush(Follow.of(follower, following));
    }
}
