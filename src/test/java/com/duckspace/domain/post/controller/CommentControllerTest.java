package com.duckspace.domain.post.controller;

import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.post.service.CommentService;
import com.duckspace.domain.post.service.ReportService;
import com.duckspace.global.auth.AuthUser;
import com.duckspace.global.auth.JwtAccessDeniedHandler;
import com.duckspace.global.auth.JwtAuthenticationEntryPoint;
import com.duckspace.global.auth.JwtAuthenticationFilter;
import com.duckspace.global.auth.JwtTokenProvider;
import com.duckspace.global.auth.Role;
import com.duckspace.global.config.CorsConfig;
import com.duckspace.global.config.SecurityConfig;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link PostControllerTest}와 같은 이유로 추가한 HTTP 계층 테스트입니다.
 * cursor/size 쿼리 파라미터가 서비스로 그대로 전달되는지, 중복 신고 예외가 실제로 409로 나가는지 확인합니다.
 */
@WebMvcTest(CommentController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;
    @MockitoBean
    private ReportService reportService;

    @Test
    void list_cursor와_size를_그대로_서비스에_전달한다() throws Exception {
        given(commentService.list(eq(1L), eq(10L), eq(5L), eq(10))).willReturn(java.util.List.of());

        mockMvc.perform(get("/api/posts/1/comments")
                        .with(user(new AuthUser(10L, Role.USER)))
                        .param("cursor", "5")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(commentService).list(1L, 10L, 5L, 10);
    }

    @Test
    void reportComment_이미_신고했으면_409() throws Exception {
        willThrow(new BusinessException(PostErrorCode.ALREADY_REPORTED))
                .given(reportService).reportComment(eq(10L), eq(5L), any());

        mockMvc.perform(post("/api/comments/5/report")
                        .with(user(new AuthUser(10L, Role.USER))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ALREADY_REPORTED"));
    }
}
