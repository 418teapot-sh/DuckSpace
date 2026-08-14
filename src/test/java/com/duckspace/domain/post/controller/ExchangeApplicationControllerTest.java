package com.duckspace.domain.post.controller;

import com.duckspace.domain.post.dto.request.ExchangeApplicationRequest;
import com.duckspace.domain.post.entity.ExchangeApplication;
import com.duckspace.domain.post.service.ExchangeApplicationService;
import com.duckspace.global.auth.AuthUser;
import com.duckspace.global.auth.JwtAccessDeniedHandler;
import com.duckspace.global.auth.JwtAuthenticationEntryPoint;
import com.duckspace.global.auth.JwtAuthenticationFilter;
import com.duckspace.global.auth.JwtTokenProvider;
import com.duckspace.global.config.CorsConfig;
import com.duckspace.global.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExchangeApplicationController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class ExchangeApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExchangeApplicationService exchangeApplicationService;

    @Test
    void apply_offeredItemName_없으면_400() throws Exception {
        ExchangeApplicationRequest request = new ExchangeApplicationRequest(" ", null, null, null, null);

        mockMvc.perform(post("/api/posts/exchange/1/applications")
                        .with(user(new AuthUser(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void apply_인증없으면_401() throws Exception {
        ExchangeApplicationRequest request = new ExchangeApplicationRequest("인형", null, null, null, null);

        mockMvc.perform(post("/api/posts/exchange/1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apply_message_길이초과면_400() throws Exception {
        String tooLongMessage = "a".repeat(ExchangeApplication.MESSAGE_MAX_LENGTH + 1);
        ExchangeApplicationRequest request = new ExchangeApplicationRequest("인형", null, null, null, tooLongMessage);

        mockMvc.perform(post("/api/posts/exchange/1/applications")
                        .with(user(new AuthUser(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listMine_인증없으면_401() throws Exception {
        mockMvc.perform(get("/api/applications").param("filter", "sent"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accept_인증없으면_401() throws Exception {
        mockMvc.perform(patch("/api/applications/1/accept"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancel_인증없으면_401() throws Exception {
        mockMvc.perform(delete("/api/applications/1"))
                .andExpect(status().isUnauthorized());
    }
}
