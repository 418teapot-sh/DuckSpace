package com.duckspace.domain.exhibition.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
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
 *   대기열 최악 = 큐 길이 x 업로드 상한 = 20 x 10MB = 200MB
 * </pre>
 *
 * <p><b>여기에 처리 중인 스레드의 메모리가 더해집니다.</b> 대기열만 세는 건 절반만 본 것이라
 * 실제 상한을 크게 낮잡습니다. 작업 하나가 잡는 최악은 <b>디코딩된 원본</b>입니다 —
 * {@code ImageInspector.MAX_PIXELS}(4천만)까지 받아주므로, JPEG 는 픽셀당 3바이트로 약 120MB,
 * 알파가 있는 PNG 는 4바이트로 약 160MB 입니다. 작업 이미지는 긴 변 1024px 로 줄인 뒤라
 * 4MB 남짓이어서 무시할 수준입니다.
 *
 * <pre>
 *   처리 중 최악 = 스레드 2개 x 약 160MB = 320MB
 *   전체 최악    = 200MB(대기열) + 320MB(처리 중) = 약 520MB
 * </pre>
 *
 * <p>이 경로는 예외적이지 않습니다 — remove.bg 무료 쿼터(월 50회)를 넘기면 그 뒤로는
 * <b>모든 업로드가</b> 원본을 직접 디코딩하는 폴백을 탑니다.
 *
 * <p>줄이려면 {@code MAX_PIXELS} 를 낮추는 게 가장 효과가 큰데, 그러면 지금 받아주던
 * 사진이 새로 거부됩니다. 지금은 상한을 유지하고 <b>업로드 시점에</b> 걸러서(400
 * {@code IMAGE_DIMENSION_TOO_LARGE}) 큐를 차지하지 않게 하는 쪽을 택했습니다.
 *
 * <p>넘치면 거절되고 해당 굿즈는 FAILED 로 정리됩니다(요청 스레드로 떠넘기지 않습니다).
 * 사용자는 다시 시도할 수 있습니다.
 */
@Configuration
public class ExhibitionAsyncConfig {

    public static final String IMAGE_EXECUTOR = "exhibitionImageExecutor";
    public static final String CLEANUP_EXECUTOR = "exhibitionCleanupExecutor";

    /** 위 주석의 계산 근거. 업로드 상한(10MB)을 바꾸면 여기도 같이 봐야 합니다. */
    private static final int QUEUE_CAPACITY = 20;

    /**
     * <b>{@code @DependsOn} 은 종료 순서 때문에 붙였습니다.</b>
     *
     * <p>스프링은 <b>생성의 역순</b>으로 빈을 파괴합니다. 선언 순서대로 두면 정리 실행기가
     * 나중에 만들어져 <b>먼저</b> 종료되는데, 그때 이미지 실행기는 아직 최대 60초의 배수 시간이
     * 남아 있습니다. 그 사이 이미지 작업이 부르는
     * {@code ImageCleanup.delete/deleteOrphan} 은 이미 닫힌 실행기에서 거절되어
     * 인라인 폴백으로 떨어집니다 — 배수 예산을 잡아둔 바로 그 구간에 DB 조회와 저장소 왕복이
     * 이미지 스레드로 들어옵니다.
     *
     * <p>정리 실행기를 <b>먼저 만들면</b> 역순 파괴 규칙에 따라 <b>가장 나중에</b> 닫힙니다.
     * 동작이 우연한 선언 순서에 의존하지 않도록 의존성으로 못박습니다.
     */
    @Bean(IMAGE_EXECUTOR)
    @DependsOn(CLEANUP_EXECUTOR)
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

    /**
     * 삭제된 이미지를 치우는 전용 실행기.
     *
     * <p><b>이미지 처리와 나눠 놓은 이유:</b> 장식장 하나를 지우면 안에 있던 굿즈 수만큼 S3 왕복이
     * 생깁니다. 그걸 위 실행기에 넣으면 스레드 둘 중 하나가 삭제만 하느라 오래 묶여서,
     * 그동안 올라온 사진들이 처리되지 못하고 큐에 밀립니다. 정리 작업 때문에 사용자가 방금 올린
     * 사진이 늦어지는 건 우선순위가 거꾸로입니다.
     *
     * <p>대기 작업이 URL 문자열만 들고 있어 메모리 걱정이 없으므로, 이쪽 큐는 넉넉히 잡습니다.
     * (이미지 실행기는 원본 바이트를 들고 있어서 큐를 짧게 잡아야 합니다)
     */
    @Bean(CLEANUP_EXECUTOR)
    public Executor exhibitionCleanupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("exhibition-cleanup-");
        // 넘치면 호출부가 그 자리에서 지웁니다. (ImageCleanup 이 처리)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 정리가 끝나기 전에 프로세스가 내려가면 객체가 그대로 남습니다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
