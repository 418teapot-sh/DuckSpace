package com.duckspace.global.config;

import com.duckspace.domain.exhibition.controller.ExhibitionSearchController;
import com.duckspace.domain.exhibition.service.ExhibitionService;
import com.duckspace.global.auth.JwtAccessDeniedHandler;
import com.duckspace.global.auth.JwtAuthenticationEntryPoint;
import com.duckspace.global.auth.JwtAuthenticationFilter;
import com.duckspace.global.auth.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * actuator 노출 범위를 고정합니다.
 *
 * <p><b>{@code /actuator/health} 는 반드시 공개여야 합니다</b> — 배포 워크플로우가 기동 확인과
 * 롤백 판정에 토큰 없이 이걸 부릅니다({@code deploy.yml} 의 헬스체크 루프). 여기가 막히면
 * <b>모든 배포가 실패하고 이전 버전으로 되돌아갑니다.</b>
 *
 * <p>반대로 나머지는 열려 있으면 안 됩니다. 예전엔 {@code /actuator/**} 였는데, 그러면 나중에
 * 메트릭을 붙이려고 {@code management.endpoints.web.exposure.include} 를 추가하는 순간
 * {@code /actuator/env} · {@code /actuator/heapdump} 까지 무인증이 됩니다.
 *
 * <p>이 슬라이스에는 actuator 엔드포인트 자체가 없어서, <b>공개면 401 이 아닌 것</b>(핸들러가
 * 없어 404)으로, <b>보호되면 401</b> 로 갈립니다. 그 차이로 매처만 검증합니다.
 */
@WebMvcTest(ExhibitionSearchController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class ActuatorExposureSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExhibitionService exhibitionService;

    @Test
    @DisplayName("배포 헬스체크가 쓰는 /actuator/health 는 토큰 없이 통과한다")
    void health_는_공개() throws Exception {
        // 여기가 401 이 되면 배포가 전부 롤백됩니다.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());   // 통과했고, 이 슬라이스엔 핸들러가 없어 404
    }

    @Test
    @DisplayName("/actuator/info 도 공개다")
    void info_는_공개() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("나머지 actuator 는 토큰이 있어야 한다")
    void 나머지는_보호된다() throws Exception {
        // env·configprops 는 설정값을, heapdump 는 힙 전체를 내줍니다(마스킹 없음).
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/configprops")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/heapdump")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/beans")).andExpect(status().isUnauthorized());
    }
}
