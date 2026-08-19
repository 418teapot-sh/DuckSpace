package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.entity.ItemStatus;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 강제 종료(OOM 등)로 처리가 끊긴 채 <b>방치된 {@code PENDING}</b> 판정.
 *
 * <p>정상 종료는 처리 완료를 기다리지만 프로세스가 그냥 죽으면 {@code PENDING} 이
 * 영원히 남는데, 재시도가 {@code FAILED} 만 받으면 사용자가 복구할 방법이 없습니다.
 * 그래서 <b>오래 방치된 PENDING 은 실패한 것으로 간주</b>하고 재시도를 허용합니다.
 *
 * <p>기준을 넉넉히 잡은 이유: 처리 큐가 꽉 찼을 때 최악 대기가 10분을 넘을 수 있습니다
 * (큐 20 x remove.bg 타임아웃 60초 / 스레드 2). 아직 살아있는 작업과 겹치더라도,
 * 결과 기록은 PENDING 가드가 선착순으로 지키고 늦은 쪽의 업로드는 회수되므로
 * 낭비일 뿐 데이터가 깨지지는 않습니다.
 *
 * <p>굿즈와 보관함 사진이 같은 파이프라인을 타므로 판정도 한 곳에 둡니다.
 * 두 서비스에 따로 두면 정책이 바뀔 때 한쪽만 고치는 드리프트가 생깁니다.
 */
final class AbandonedPending {

    static final Duration THRESHOLD = Duration.ofMinutes(15);

    private AbandonedPending() {
    }

    static boolean isAbandoned(ItemStatus status, LocalDateTime updatedAt) {
        return status == ItemStatus.PENDING
                && updatedAt != null
                && updatedAt.isBefore(LocalDateTime.now().minus(THRESHOLD));
    }
}
