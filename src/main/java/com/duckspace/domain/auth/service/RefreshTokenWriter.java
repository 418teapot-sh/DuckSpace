package com.duckspace.domain.auth.service;

import com.duckspace.domain.auth.entity.RefreshToken;
import com.duckspace.domain.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code saveRefreshToken}의 INSERT 시도를 별도 트랜잭션(REQUIRES_NEW)으로 분리합니다.
 *
 * <p>같은 트랜잭션 안에서 {@code saveAndFlush}가 유니크 제약 위반으로 실패하면, Hibernate 세션이
 * 오염되어(entry has a null identifier) 이후 같은 세션에서의 조회가 {@code AssertionFailure}로 죽습니다.
 * INSERT를 별도 트랜잭션·별도 영속성 컨텍스트로 분리해두면, 실패해도 그 트랜잭션만 롤백되고
 * 호출부(바깥 트랜잭션)는 오염되지 않아 재조회·복구를 이어갈 수 있습니다.
 */
@Component
@RequiredArgsConstructor
class RefreshTokenWriter {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(Long userId, String tokenHash) {
        refreshTokenRepository.saveAndFlush(new RefreshToken(userId, tokenHash));
    }
}
