package com.duckspace.domain.post.controller;

import com.duckspace.domain.post.dto.request.CasualPostRequest;
import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.service.LikeService;
import com.duckspace.domain.post.service.PostImageService;
import com.duckspace.domain.post.service.PostService;
import com.duckspace.domain.post.service.ReportService;
import com.duckspace.global.auth.AuthUser;
import com.duckspace.global.auth.JwtAccessDeniedHandler;
import com.duckspace.global.auth.JwtAuthenticationEntryPoint;
import com.duckspace.global.auth.JwtAuthenticationFilter;
import com.duckspace.global.auth.JwtTokenProvider;
import com.duckspace.global.auth.Role;
import com.duckspace.global.config.CorsConfig;
import com.duckspace.global.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 서비스 레이어 Mockito 테스트만으로는 {@code @Valid}가 실제로 400을 내는지,
 * 인증이 없을 때 정말 401이 나가는지는 검증되지 않습니다. 이 테스트는 그 HTTP 계층을 확인합니다.
 *
 * <p>{@code @WebMvcTest}는 Controller/ControllerAdvice/Filter만 자동으로 스캔하고 나머지
 * {@code @Component}는 스캔하지 않으므로, SecurityConfig가 참조하는 빈들을 명시적으로 {@code @Import}합니다.
 */
@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PostService postService;
    @MockitoBean
    private LikeService likeService;
    @MockitoBean
    private ReportService reportService;
    @MockitoBean
    private PostImageService postImageService;

    @Test
    void createCasual_길이초과면_400() throws Exception {
        String tooLongContent = "a".repeat(Post.CASUAL_CONTENT_MAX_LENGTH + 1);
        CasualPostRequest request = new CasualPostRequest(tooLongContent, null, null);

        mockMvc.perform(post("/api/posts/casual")
                        .with(user(new AuthUser(1L, Role.USER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createCasual_인증없으면_401() throws Exception {
        CasualPostRequest request = new CasualPostRequest("본문", null, null);

        mockMvc.perform(post("/api/posts/casual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadImage_정상이면_URL을_돌려준다() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "goods.png", "image/png", new byte[]{1, 2, 3});
        given(postImageService.upload(eq(1L), any())).willReturn("https://cdn/posts/1/abc.png");

        mockMvc.perform(multipart("/api/posts/images")
                        .file(image)
                        .with(user(new AuthUser(1L, Role.USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.imageUrl").value("https://cdn/posts/1/abc.png"));
    }

    @Test
    void uploadImage_인증없으면_401() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "goods.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/posts/images").file(image))
                .andExpect(status().isUnauthorized());
    }
}
