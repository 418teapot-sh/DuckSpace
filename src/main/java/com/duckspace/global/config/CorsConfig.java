package com.duckspace.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 프론트엔드 연동을 위한 CORS 설정.
 * 허용 주소는 application.yml 의 {@code cors.allowed-origins} 에서 관리합니다.
 *
 * <p>정확히 일치하는 주소 대신 <b>패턴</b>으로 등록합니다.
 * Vercel 등은 브랜치·커밋마다 프리뷰 주소가 새로 생기므로 패턴이 필요합니다.
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // TraceIdFilter 가 실어 보내는 X-Trace-Id 를 프론트가 읽을 수 있어야 합니다 — CORS 는
        // 노출 목록에 없는 헤더를 크로스 오리진 JS 에서 못 읽게 막습니다. 연동 가이드에 "응답
        // 헤더로도 온다" 고 적어뒀는데 실제로는 읽히지 않는 상태였습니다.
        // (예전에 들어 있던 Authorization 은 요청 헤더라 이 API 가 응답에 실은 적이 없습니다)
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
