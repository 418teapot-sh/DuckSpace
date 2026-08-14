package com.duckspace.domain.exhibition.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 이미지 처리를 백그라운드로 돌리기 위한 실행기.
 *
 * <p><b>스레드 수를 일부러 작게 잡았습니다.</b> 사진 한 장을 {@code BufferedImage} 로 펼치면
 * 4000x3000 기준 약 48MB 를 차지합니다. 동시 처리가 많아지면 작은 EC2 인스턴스에서
 * 메모리가 먼저 터집니다. 큐에 쌓아두고 순서대로 처리하는 편이 안전합니다.
 *
 * <h2>큐 길이는 스레드 수만큼 중요합니다</h2>
 *
 * <p>대기 중인 작업이 <b>원본 바이트를 그대로 들고 있습니다.</b> 업로드 상한이 10MB 이므로
 * 큐 길이가 곧 메모리 상한입니다. 스레드만 줄이고 큐를 길게 잡으면 메모리를 아끼려던 의도가
 * 무너집니다 — 예전에 100 이었을 때는 최악의 경우 <b>대기열만 1GB</b> 였습니다.
 *
 * <pre>
 *   최악 = 큐 길이 x 업로드 상한 = 20 x 10MB = 200MB
 * </pre>
 *
 * <p>넘치면 거절되고 해당 굿즈는 FAILED 로 정리됩니다(요청 스레드로 떠넘기지 않습니다).
 * 사용자는 다시 시도할 수 있습니다.
 */
@Configuration
public class ExhibitionAsyncConfig {

    public static final String IMAGE_EXECUTOR = "exhibitionImageExecutor";

    /** 위 주석의 계산 근거. 업로드 상한(10MB)을 바꾸면 여기도 같이 봐야 합니다. */
    private static final int QUEUE_CAPACITY = 20;

    @Bean(IMAGE_EXECUTOR)
    public Executor exhibitionImageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("exhibition-image-");

        // 큐까지 가득 차면 거절합니다. 예전에는 CallerRunsPolicy 로 요청 스레드에 떠넘겼는데,
        // 그러면 "접수만 하고 즉시 응답한다" 는 설계가 정확히 부하가 몰린 순간에 무너집니다.
        // 거절은 ExhibitionImageProcessor 가 받아서 해당 아이템을 FAILED 로 정리합니다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        // 배포마다 systemd 가 프로세스를 재시작합니다. 이 설정이 없으면 종료 시 shutdownNow() 가
        // remove.bg 호출 중인 스레드를 인터럽트해서, 처리 중이던 사진이 매 배포마다 깨집니다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
