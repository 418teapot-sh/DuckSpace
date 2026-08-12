package com.duckspace.domain.exhibition.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 이미지 처리를 백그라운드로 돌리기 위한 실행기.
 *
 * <p><b>스레드 수를 일부러 작게 잡았습니다.</b> 사진 한 장을 {@code BufferedImage} 로 펼치면
 * 4000x3000 기준 약 48MB 를 차지합니다. 동시 처리가 많아지면 작은 EC2 인스턴스에서
 * 메모리가 먼저 터집니다. 큐에 쌓아두고 순서대로 처리하는 편이 안전합니다.
 */
@Configuration
@EnableAsync
public class ExhibitionAsyncConfig {

    public static final String IMAGE_EXECUTOR = "exhibitionImageExecutor";

    @Bean(IMAGE_EXECUTOR)
    public Executor exhibitionImageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("exhibition-image-");
        // 큐가 가득 차면 요청 스레드에서 처리합니다. 업로드를 거부하는 것보다는 느려지는 편이 낫습니다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
