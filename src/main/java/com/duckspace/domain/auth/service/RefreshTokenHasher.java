package com.duckspace.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 리프레시 토큰을 DB에 평문으로 저장하지 않기 위한 해시 유틸리티.
 * 토큰 자체가 JWT(고엔트로피 랜덤값)라 비밀번호와 달리 느린 해시(BCrypt)가 필요 없어 SHA-256을 씁니다.
 */
final class RefreshTokenHasher {

    private RefreshTokenHasher() {
    }

    static String hash(String rawToken) {
        return Base64.getEncoder().encodeToString(sha256(rawToken));
    }

    static boolean matches(String rawToken, String hashedToken) {
        return MessageDigest.isEqual(
                sha256(rawToken),
                Base64.getDecoder().decode(hashedToken)
        );
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
