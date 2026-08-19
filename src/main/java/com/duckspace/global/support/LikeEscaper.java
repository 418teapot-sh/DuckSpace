package com.duckspace.global.support;

/**
 * LIKE 검색 키워드 이스케이프.
 *
 * <p>사용자가 입력한 {@code %} · {@code _} 가 와일드카드로 동작하지 않도록 이스케이프합니다.
 * 쿼리에서는 {@code escape '\\'} 를 같이 써야 합니다.
 */
public final class LikeEscaper {

    private LikeEscaper() {
    }

    public static String escape(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
