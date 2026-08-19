package com.duckspace.domain.popup.controller;

import com.duckspace.domain.popup.service.PopupLikeService;
import com.duckspace.domain.popup.service.PopupService;
import com.duckspace.global.auth.JwtAccessDeniedHandler;
import com.duckspace.global.auth.JwtAuthenticationEntryPoint;
import com.duckspace.global.auth.JwtAuthenticationFilter;
import com.duckspace.global.auth.JwtTokenProvider;
import com.duckspace.global.config.CorsConfig;
import com.duckspace.global.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/popups/** 는 조회(GET)만 공개이고, 찜(POST/DELETE .../like)은 같은 경로 아래
 * 있어도 토큰이 필요합니다. SecurityConfig.PUBLIC_GET_ENDPOINTS 설정이 실수로
 * /api/popups/** 통짜 공개로 되돌아가는 회귀를 HTTP 계층에서 직접 잡습니다.
 */
@WebMvcTest(PopupController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class PopupControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PopupService popupService;
    @MockitoBean
    private PopupLikeService popupLikeService;

    @Test
    @DisplayName("비로그인은 찜 등록을 할 수 없다")
    void 찜_등록은_차단() throws Exception {
        mockMvc.perform(post("/api/popups/1/like"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비로그인은 찜 해제를 할 수 없다")
    void 찜_해제는_차단() throws Exception {
        mockMvc.perform(delete("/api/popups/1/like"))
                .andExpect(status().isUnauthorized());
    }
}
