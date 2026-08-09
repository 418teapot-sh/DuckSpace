package com.duckspace.domain.auth.service;

import com.duckspace.domain.auth.exception.AuthErrorCode;
import com.duckspace.global.exception.BusinessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 이메일당 로그인 시도 횟수를 세서 무차별 대입(brute force)을 막는다.
 * 단일 서버(EC2 한 대) 구성이라 인메모리로 충분하며, 서버 재시작 시 초기화돼도 무방하다.
 * 검사와 증가를 하나의 {@code compute} 안에서 원자적으로 처리해, 병렬 요청이 검사를 동시에 통과하는 것을 막는다.
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
        AtomicBoolean blocked = new AtomicBoolean(false);
        attempts.compute(email, (key, existing) -> {
            Attempt current = (existing == null || existing.isExpired())
                    ? new Attempt(new AtomicInteger(0), Instant.now())
                    : existing;
            if (current.count().get() >= MAX_ATTEMPTS) {
                blocked.set(true);
                return current;
            }
            current.count().incrementAndGet();
            return current;
        });

        if (blocked.get()) {
            throw new BusinessException(AuthErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
    }

    void onSuccess(String email) {
        attempts.remove(email);
    }

    @Scheduled(fixedRate = 15, initialDelay = 15, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void evictExpired() {
        attempts.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
