package com.duckspace.domain.user.controller;

import com.duckspace.domain.user.service.UserSearchService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 검색 결과(GET /api/search/users)는 비로그인도 볼 수 있지만, 같은 프리픽스 아래
 * 검색 내역(.../history)은 유저 개인 데이터라 토큰이 필요합니다.
 */
@WebMvcTest(UserSearchController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class UserSearchControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSearchService userSearchService;

    @Test
    @DisplayName("비로그인도 유저를 검색할 수 있다")
    void 검색은_공개() throws Exception {
        given(userSearchService.search(any(), any())).willReturn(List.of());

        mockMvc.perform(get("/api/search/users").param("keyword", "치이카와"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("검색 내역 조회는 토큰이 필요하다")
    void 내역_조회는_차단() throws Exception {
        mockMvc.perform(get("/api/search/users/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("검색 결과 클릭 기록은 토큰이 필요하다")
    void 내역_기록은_차단() throws Exception {
        mockMvc.perform(post("/api/search/users/history")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("검색 내역 전체 삭제는 토큰이 필요하다")
    void 내역_삭제는_차단() throws Exception {
        mockMvc.perform(delete("/api/search/users/history"))
                .andExpect(status().isUnauthorized());
    }
}
