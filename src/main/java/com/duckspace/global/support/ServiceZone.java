package com.duckspace.global.support;

import java.time.ZoneId;

/**
 * 서비스 기준 시간대.
 *
 * <p>서버(JVM) 기본 타임존에 기대지 않도록, 날짜·시각이 필요한 곳은 전부 이 상수를
 * 명시적으로 씁니다. {@code ZoneId.of("Asia/Seoul")}이 도메인마다 따로따로 박혀있으면
 * 나중에 시간대를 바꿀 때 하나를 빠뜨리기 쉬워 한 곳으로 모았습니다.
 */
public final class ServiceZone {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private ServiceZone() {
    }
}
