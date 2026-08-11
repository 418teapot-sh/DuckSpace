package com.duckspace.domain.popup;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PopupAdminSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void 관리자가_팝업을_등록하면_AI요약_필드까지_포함해서_응답한다() throws Exception {
        String createBody = """
                {
                  "title": "치이카와 팝업스토어",
                  "imageUrl": "https://example.com/chiikawa.png",
                  "description": "성수동에서 열리는 치이카와 팝업스토어입니다.",
                  "location": "성수동",
                  "startDate": "2026-09-01",
                  "endDate": "2026-09-20"
                }
                """;

        String createResponse = mockMvc.perform(post("/api/admin/popups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("치이카와 팝업스토어"))
                .andExpect(jsonPath("$.data.status").value("UPCOMING"))
                .andExpect(jsonPath("$.data").isMap())
                .andReturn().getResponse().getContentAsString();

        System.out.println("CREATE RESPONSE = " + createResponse);

        mockMvc.perform(get("/api/admin/popups"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        Number popupId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.id");
        long popupIdLong = popupId.longValue();

        String updateBody = """
                {
                  "title": "치이카와 팝업스토어 (연장)",
                  "imageUrl": "https://example.com/chiikawa.png",
                  "description": "인기가 많아 기간이 연장되었습니다.",
                  "location": "성수동",
                  "startDate": "2026-08-01",
                  "endDate": "2026-08-31"
                }
                """;

        mockMvc.perform(patch("/api/admin/popups/" + popupIdLong)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("치이카와 팝업스토어 (연장)"))
                .andExpect(jsonPath("$.data.endDate").value("2026-08-31"));

        mockMvc.perform(delete("/api/admin/popups/" + popupIdLong))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/popups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}