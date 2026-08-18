package com.duckspace.domain.popup.client;

import com.duckspace.global.support.openai.AbstractOpenAiSummaryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class OpenAiSummaryClient extends AbstractOpenAiSummaryClient {

    private static final String SYSTEM_PROMPT =
            "해당 클라이언트는 팝업스토어 일정을 10문장 이내로 정리해주는 어시스턴트야. 시작일과 종료일, 위치를 반드시 포함해서 안내해줘.";

    public OpenAiSummaryClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model
    ) {
        super(apiKey, model);
    }

    public String summarizeSchedule(String title, String description, String location,
                                     LocalDate startDate, LocalDate endDate) {
        String safeLocation = (location == null || location.isBlank()) ? "위치 정보 없음" : location;
        String safeDescription = (description == null || description.isBlank()) ? "설명 없음" : description;
        String userContent = """
                  제목: %s
                  기간: %s ~ %s
                  위치: %s
                  설명: %s
                  """.formatted(title, startDate, endDate, safeLocation, safeDescription);

        return summarize(SYSTEM_PROMPT, userContent, "팝업");
    }
}
