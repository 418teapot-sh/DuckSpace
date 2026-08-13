package com.duckspace.domain.home;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 데이터가_없어도_홈_응답은_섹션_구조를_유지한다() throws Exception {
        mockMvc.perform(get("/api/home"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.banners").isArray())
                .andExpect(jsonPath("$.data.upcomingPopups").isArray());
    }

    @Test
    @WithMockUser
    void 배너_팝업을_등록하면_홈_응답에_그대로_모여서_내려온다() throws Exception {
        String popupCreateBody = """
                {
                  "title": "홈테스트 팝업",
                  "imageUrl": "https://example.com/popup.png",
                  "description": "홈 aggregation 테스트용 팝업",
                  "location": "성수동",
                  "startDate": "2026-08-01",
                  "endDate": "2026-12-31"
                }
                """;
        String popupResponse = mockMvc.perform(post("/api/admin/popups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(popupCreateBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long popupId = com.jayway.jsonpath.JsonPath.<Number>read(popupResponse, "$.data.id").longValue();

        String bannerCreateBody = """
                {
                  "imageUrl": "https://example.com/banner.png",
                  "title": "홈테스트 배너",
                  "description": "홈 aggregation 테스트용 배너",
                  "popupId": %d,
                  "startAt": "2020-01-01T00:00:00",
                  "endAt": "2099-01-01T00:00:00",
                  "sortOrder": 0
                }
                """.formatted(popupId);
        String bannerResponse = mockMvc.perform(post("/api/admin/banners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bannerCreateBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long bannerId = com.jayway.jsonpath.JsonPath.<Number>read(bannerResponse, "$.data.id").longValue();

        try {
            mockMvc.perform(get("/api/home"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.banners[?(@.title == '홈테스트 배너')]").exists())
                    .andExpect(jsonPath("$.data.banners[?(@.popupId == %d)]".formatted(popupId)).exists())
                    .andExpect(jsonPath("$.data.upcomingPopups[?(@.title == '홈테스트 팝업')]").exists());
        } finally {
            // 다른 도메인의 테스트가 같은 인메모리 DB를 공유하므로, 만든 데이터는 반드시 정리한다.
            mockMvc.perform(delete("/api/admin/banners/" + bannerId));
            mockMvc.perform(delete("/api/admin/popups/" + popupId));
        }
    }
}
