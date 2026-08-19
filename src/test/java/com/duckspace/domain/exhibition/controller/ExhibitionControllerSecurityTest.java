package com.duckspace.domain.exhibition.controller;

import com.duckspace.domain.exhibition.dto.response.ExhibitionDetailResponse;
import com.duckspace.domain.exhibition.service.ExhibitionItemService;
import com.duckspace.domain.exhibition.service.ExhibitionLikeService;
import com.duckspace.domain.exhibition.service.ExhibitionService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 전시 조회는 비로그인도 볼 수 있어야 하지만, <b>같은 경로의 수정·삭제는 막혀 있어야</b> 합니다.
 *
 * <p>{@code /api/exhibitions/{id}} 하나에 GET·PATCH·DELETE 가 같이 걸려 있어서, 경로만으로
 * 열면 아무나 남의 장식장을 지울 수 있게 됩니다. 설정 한 줄 차이로 그렇게 되는 자리라
 * HTTP 계층에서 직접 확인합니다.
 */
@WebMvcTest({ExhibitionController.class, ExhibitionSearchController.class})
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class ExhibitionControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExhibitionService exhibitionService;
    @MockitoBean
    private ExhibitionItemService exhibitionItemService;
    @MockitoBean
    private ExhibitionLikeService exhibitionLikeService;

    private ExhibitionDetailResponse detail() {
        return new ExhibitionDetailResponse(
                3L, "내 장식장", "BASIC", 1L, false, 0, false, LocalDateTime.now(), List.of());
    }

    // ------------------------------------------------------------------
    // 열려 있어야 하는 것
    // ------------------------------------------------------------------

    @Test
    @DisplayName("비로그인도 인기 전시장을 볼 수 있다")
    void 인기_전시장은_공개() throws Exception {
        given(exhibitionService.getPopular(any(), isNull())).willReturn(List.of());

        mockMvc.perform(get("/api/exhibitions/popular"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비로그인도 검색 탭 장식장 피드를 볼 수 있다")
    void 장식장_피드는_공개() throws Exception {
        given(exhibitionService.getRecent(any(), isNull())).willReturn(List.of());

        mockMvc.perform(get("/api/exhibitions"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비로그인도 장식장 상세를 볼 수 있다")
    void 상세는_공개() throws Exception {
        given(exhibitionService.getDetail(eq(3L), isNull())).willReturn(detail());

        mockMvc.perform(get("/api/exhibitions/3"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비로그인도 전시를 검색할 수 있다")
    void 검색은_공개() throws Exception {
        given(exhibitionService.search(any(), any(), isNull())).willReturn(List.of());

        mockMvc.perform(get("/api/search/exhibitions").param("keyword", "치이카와"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // 막혀 있어야 하는 것 — 여기가 진짜 확인하고 싶은 부분입니다
    // ------------------------------------------------------------------

    @Test
    @DisplayName("상세와 경로가 같아도 수정은 토큰이 필요하다")
    void 같은_경로의_수정은_차단() throws Exception {
        mockMvc.perform(patch("/api/exhibitions/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"바뀐 이름\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("상세와 경로가 같아도 삭제는 토큰이 필요하다")
    void 같은_경로의_삭제는_차단() throws Exception {
        mockMvc.perform(delete("/api/exhibitions/3"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("장식장 생성은 토큰이 필요하다")
    void 생성은_차단() throws Exception {
        mockMvc.perform(post("/api/exhibitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새 장식장\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("좋아요는 토큰이 필요하다")
    void 좋아요는_차단() throws Exception {
        mockMvc.perform(post("/api/exhibitions/3/like"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("굿즈 그리드는 아직 공개 대상이 아니다")
    void 굿즈_그리드는_차단() throws Exception {
        // 상세 응답이 굿즈를 전부 담고 있어서 지금은 열 필요가 없습니다.
        // 프론트가 이 경로로 더보기를 붙이게 되면 그때 같이 열어야 합니다.
        mockMvc.perform(get("/api/exhibitions/3/items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 장식장 목록은 토큰이 필요하다")
    void 내_장식장_목록은_차단() throws Exception {
        // 공개 패턴을 {id:[0-9]+} 로 좁혀둔 이유가 이것입니다. /api/exhibitions/* 로 뒀다면
        // "내 장식장" 이 통째로 공개됐을 겁니다.
        mockMvc.perform(get("/api/exhibitions/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("재처리는 토큰이 필요하다")
    void 재처리는_차단() throws Exception {
        mockMvc.perform(post("/api/exhibitions/3/items/9/retry"))
                .andExpect(status().isUnauthorized());
    }
}
