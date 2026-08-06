package com.duckspace.global.auth.oauth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth2 로그인 성공 후 프론트엔드로 access/refresh 토큰 대신 1회용 코드를 전달하기 위한 임시 저장소.
 * 코드는 30초 안에 {@code /api/auth/token/exchange}로 교환되지 않으면 만료된다.
 */
@Component
public class AuthorizationCodeStore {

    private record CodeEntry(Long userId, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final Map<String, CodeEntry> store = new ConcurrentHashMap<>();
    private static final Duration CODE_TTL = Duration.ofSeconds(30);

    public String createCode(Long userId) {
        store.values().removeIf(CodeEntry::isExpired);
        String code = UUID.randomUUID().toString();
        store.put(code, new CodeEntry(userId, Instant.now().plus(CODE_TTL)));
        return code;
    }

    public Optional<Long> consumeCode(String code) {
        CodeEntry entry = store.remove(code);
        if (entry == null || entry.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }
}
