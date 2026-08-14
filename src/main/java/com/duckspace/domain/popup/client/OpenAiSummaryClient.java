package com.duckspace.domain.popup.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

@Component
public class OpenAiSummaryClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiSummaryClient.class);

    private static final String SYSTEM_PROMPT =
            "해당 클라이언트는 팝업스토어 일정을 10문장 이내로 정리해주는 어시스턴트야. 시작일과 종료일, 위치를 반드시 포함해서 안내해줘.";

    private final RestClient restClient;
    private final String model;
    private final boolean enabled;

    public OpenAiSummaryClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model
    ) {
        this.model = model;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer "+apiKey)
                .build();
    }

    public String summarizeSchedule(String title, String description, LocalDate startDate, LocalDate endDate) {
        if (!enabled) {
            log.warn("OpenAI API 키가 설정되지 않아 팝업 AI 요약을 건너뜁니다.");
            return null;
        }

        String userContent = """
                  제목: %s
                  기간: %s ~ %s
                  설명: %s
                  """.formatted(title, startDate, endDate, description);

        try {
            ChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(new ChatRequest(model, List.of(
                            new ChatMessage("system", SYSTEM_PROMPT),
                            new ChatMessage("user", userContent)
                    )))
                    .retrieve()
                    .body(ChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                log.warn("OpenAI 응답에 choices가 없어 팝업 AI 요약을 건너뜁니다. response={}", response);
                return null;
            }

            return response.choices().getFirst().message().content();
        } catch (Exception e) {
            // OpenAI 장애로 팝업 등록 자체가 막히면 안 되므로 null로 폴백
            log.warn("팝업 AI 요약 요청 실패, null로 폴백합니다.", e);
            return null;
        }
    }

    private record ChatRequest(String model, List<ChatMessage> messages) {}
    private record ChatMessage(String role, String content) {}
    private record ChatResponse(List<Choice> choices) {}
    private record Choice(ChatMessage message) {}
}





