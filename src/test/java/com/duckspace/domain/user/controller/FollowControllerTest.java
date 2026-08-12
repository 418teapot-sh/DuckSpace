package com.duckspace.domain.user.controller;

import com.duckspace.domain.user.dto.response.FollowUserResponse;
import com.duckspace.domain.user.exception.UserErrorCode;
import com.duckspace.domain.user.service.FollowService;
import com.duckspace.global.auth.AuthUser;
import com.duckspace.global.auth.JwtAccessDeniedHandler;
import com.duckspace.global.auth.JwtAuthenticationEntryPoint;
import com.duckspace.global.auth.JwtAuthenticationFilter;
import com.duckspace.global.auth.JwtTokenProvider;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FollowController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class FollowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FollowService followService;

    @Test
    void follow_인증없이_요청하면_401() throws Exception {
        mockMvc.perform(post("/api/users/2/follow"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void follow_자기_자신이면_400() throws Exception {
        willThrow(new BusinessException(UserErrorCode.SELF_FOLLOW_NOT_ALLOWED))
                .given(followService).follow(eq(1L), eq(1L));

        mockMvc.perform(post("/api/users/1/follow")
                        .with(user(new AuthUser(1L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SELF_FOLLOW_NOT_ALLOWED"));
    }

    @Test
    void follow_이미_팔로우했으면_409() throws Exception {
        willThrow(new BusinessException(UserErrorCode.ALREADY_FOLLOWING))
                .given(followService).follow(eq(1L), eq(2L));

        mockMvc.perform(post("/api/users/2/follow")
                        .with(user(new AuthUser(1L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_FOLLOWING"));
    }

    @Test
    void unfollow_정상_요청은_204에_준하는_응답을_반환한다() throws Exception {
        mockMvc.perform(delete("/api/users/2/follow")
                        .with(user(new AuthUser(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(followService).unfollow(1L, 2L);
    }

    @Test
    void followers_cursor와_size를_그대로_서비스에_전달한다() throws Exception {
        given(followService.getFollowers(eq(2L), eq(5L), eq(10))).willReturn(List.of());

        mockMvc.perform(get("/api/users/2/followers")
                        .with(user(new AuthUser(1L)))
                        .param("cursor", "5")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(followService).getFollowers(2L, 5L, 10);
    }

    @Test
    void following_목록을_정상적으로_내려준다() throws Exception {
        given(followService.getFollowing(eq(2L), eq(null), eq(null)))
                .willReturn(List.of(new FollowUserResponse(3L, "닉네임")));

        mockMvc.perform(get("/api/users/2/following")
                        .with(user(new AuthUser(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(3))
                .andExpect(jsonPath("$.data[0].nickname").value("닉네임"));
    }
}
