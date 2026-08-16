package com.duckspace.global.support.openai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
public abstract class AbstractOpenAiSummaryClient {

    private final RestClient restClient;
    private final String model;
    private final boolean enabled;

    protected AbstractOpenAiSummaryClient(String apiKey, String model) {
        this.model = model;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    protected String summarize(String systemPrompt, String userContent, String logLabel) {
        if (!enabled) {
            log.warn("OpenAI API 키가 설정되지 않아 {} AI 요약을 건너뜁니다.", logLabel);
            return null;
        }

        try {
            ChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(new ChatRequest(model, List.of(
                            new ChatMessage("system", systemPrompt),
                            new ChatMessage("user", userContent)
                    )))
                    .retrieve()
                    .body(ChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                log.warn("OpenAI 응답에 choices가 없어 {} AI 요약을 건너뜁니다. response={}", logLabel, response);
                return null;
            }

            return response.choices().getFirst().message().content();
        } catch (Exception e) {
            // OpenAI 장애로 등록/수정 자체가 막히면 안 되므로 null로 폴백
            log.warn("{} AI 요약 요청 실패, null로 폴백합니다.", logLabel, e);
            return null;
        }
    }

    private record ChatRequest(String model, List<ChatMessage> messages) {}
    private record ChatMessage(String role, String content) {}
    private record ChatResponse(List<Choice> choices) {}
    private record Choice(ChatMessage message) {}
}
