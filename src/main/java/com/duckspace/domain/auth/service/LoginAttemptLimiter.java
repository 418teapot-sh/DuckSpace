package com.duckspace.domain.auth.service;

import com.duckspace.domain.auth.exception.AuthErrorCode;
import com.duckspace.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 이메일당 로그인 실패 횟수를 세서 무차별 대입(brute force)을 막는다.
 * 단일 서버(EC2 한 대) 구성이라 인메모리로 충분하며, 서버 재시작 시 초기화돼도 무방하다.
 */
@Component
class LoginAttemptLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempt(AtomicInteger count, Instant windowStart) {
        boolean isExpired() {
            return Instant.now().isAfter(windowStart.plus(WINDOW));
        }
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    void checkAllowed(String email) {
        Attempt attempt = attempts.get(email);
        if (attempt != null && !attempt.isExpired() && attempt.count().get() >= MAX_ATTEMPTS) {
            throw new BusinessException(AuthErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
    }

    void onFailure(String email) {
        attempts.compute(email, (key, existing) -> {
            if (existing == null || existing.isExpired()) {
                return new Attempt(new AtomicInteger(1), Instant.now());
            }
            existing.count().incrementAndGet();
            return existing;
        });
    }

    void onSuccess(String email) {
        attempts.remove(email);
    }
}
