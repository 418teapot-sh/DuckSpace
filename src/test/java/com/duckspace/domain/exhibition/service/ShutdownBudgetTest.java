package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.image.RemoveBgClient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 앱의 종료 대기 시간이 systemd 예산 안에 들어가는지 검사합니다.
 *
 * <p>이 둘은 <b>서로 다른 파일에 있는 한 묶음</b>입니다. 자바 상수만 올리면 systemd 가 먼저
 * {@code SIGKILL} 을 보내서 고친 효과가 없고, 유닛 파일만 올리면 앱이 먼저 포기합니다.
 * 한쪽만 고쳤을 때 조용히 넘어가지 않도록 여기서 못박습니다.
 *
 * <p>실제로 그렇게 어긋난 적이 있습니다 — 유닛 파일에 {@code TimeoutStopSec} 이 아예 없어
 * 우분투 기본값(약 90초)이 적용되던 때, 앱 쪽 대기도 60+30=90초라 경계에 딱 붙어 있었습니다.
 * 그 상태에서 앱만 100초로 올렸으면 systemd 가 90초에 잘랐을 것입니다.
 */
class ShutdownBudgetTest {

    private static final Path UNIT_FILE = Path.of("deploy", "duckspace.service");

    /** {@code TimeoutStopSec=150} 또는 {@code TimeoutStopSec = 150s} 형태를 받습니다. */
    private static final Pattern TIMEOUT_STOP_SEC =
            Pattern.compile("^\\s*TimeoutStopSec\\s*=\\s*(\\d+)s?\\s*$", Pattern.MULTILINE);

    /**
     * 실행기 종료 뒤에도 톰캣·커넥션 풀 정리가 남습니다. 예산을 정확히 꽉 채우면
     * 그 몫이 없어서 결국 잘립니다.
     */
    private static final int RESERVED_FOR_REST_OF_SHUTDOWN = 10;

    /** 처리된 이미지를 저장소에 올리는 왕복. remove.bg 예산과 달리 상수로 잡혀 있지 않아 여기 둡니다. */
    private static final int S3_ROUND_TRIP_SECONDS = 30;

    @Test
    @DisplayName("두 실행기의 대기 시간 합이 systemd TimeoutStopSec 안에 들어간다")
    void 종료_예산을_넘지_않는다() throws IOException {
        int timeoutStopSec = readTimeoutStopSec();
        int executorTotal = ExhibitionAsyncConfig.IMAGE_AWAIT_SECONDS
                + ExhibitionAsyncConfig.CLEANUP_AWAIT_SECONDS;

        assertThat(executorTotal + RESERVED_FOR_REST_OF_SHUTDOWN)
                .as("""
                        이미지 %d초 + 정리 %d초 = %d초 인데 TimeoutStopSec 은 %d초입니다.
                        두 실행기는 차례로 닫히므로 합으로 계산해야 합니다.
                        ExhibitionAsyncConfig 의 상수나 deploy/duckspace.service 중 하나를 맞추세요."""
                        .formatted(ExhibitionAsyncConfig.IMAGE_AWAIT_SECONDS,
                                ExhibitionAsyncConfig.CLEANUP_AWAIT_SECONDS,
                                executorTotal, timeoutStopSec))
                .isLessThanOrEqualTo(timeoutStopSec);
    }

    @Test
    @DisplayName("이미지 대기 시간이 작업 하나의 최악보다 길다")
    void 작업_최악을_덮는다() {
        // RemoveBgClient 의 상수를 직접 읽습니다. 예전에는 여기에 10 + 60 을 손으로 적어뒀는데,
        // 그 사이 키 로테이션(#94)이 들어오면서 remove.bg 최악이 "시도당 70초 x 키 개수" 로
        // 바뀌었는데도 이 테스트는 통과했습니다. 상수를 가져오면 그쪽이 바뀔 때 같이 걸립니다.
        int worstCaseWorkSeconds = RemoveBgClient.TOTAL_BUDGET_SECONDS + S3_ROUND_TRIP_SECONDS;

        assertThat(ExhibitionAsyncConfig.IMAGE_AWAIT_SECONDS)
                .as("""
                        remove.bg 총 예산 %d초 + S3 왕복 %d초 = %d초 인데 대기가 %d초입니다.
                        여기가 더 짧으면 remove.bg 응답을 기다리던 스레드가 배포마다 잘립니다."""
                        .formatted(RemoveBgClient.TOTAL_BUDGET_SECONDS, S3_ROUND_TRIP_SECONDS,
                                worstCaseWorkSeconds, ExhibitionAsyncConfig.IMAGE_AWAIT_SECONDS))
                .isGreaterThanOrEqualTo(worstCaseWorkSeconds);
    }

    private int readTimeoutStopSec() throws IOException {
        assertThat(UNIT_FILE)
                .as("systemd 유닛 파일이 있어야 합니다. 배포가 이 파일을 그대로 복사해 씁니다")
                .exists();

        Matcher matcher = TIMEOUT_STOP_SEC.matcher(Files.readString(UNIT_FILE));
        assertThat(matcher.find())
                .as("""
                        %s 에 TimeoutStopSec 이 없습니다.
                        없으면 systemd 기본값(우분투 약 90초)이 적용되는데, 그건 지금 대기 시간보다 짧습니다."""
                        .formatted(UNIT_FILE))
                .isTrue();
        return Integer.parseInt(matcher.group(1));
    }
}
