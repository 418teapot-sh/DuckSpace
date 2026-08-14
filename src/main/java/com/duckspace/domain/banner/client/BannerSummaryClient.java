package com.duckspace.domain.banner.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class BannerSummaryClient {

    private static final Logger log = LoggerFactory.getLogger(BannerSummaryClient.class);

    private static final String SYSTEM_PROMPT =
            "해당 클라이언트는 배너가 홍보하는 이벤트 내용을 10문장 이내로 정리해주는 어시스턴트야. "
                    + "핵심 내용과 노출 기간을 반드시 포함해서 안내해줘.";

    private final RestClient restClient;
    private final String model;

    private final boolean enabled;

    public BannerSummaryClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model
    ) {
        this.model = model;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public String summarizeBanner(String title, String description, LocalDateTime startAt, LocalDateTime endAt) {
        if (!enabled) {
            log.warn("OpenAI API 키가 설정되지 않아 배너 AI 요약을 건너뜁니다.");
            return null;
        }

        String userContent = """
                제목: %s
                노출 기간: %s ~ %s
                설명: %s
                """.formatted(title, startAt, endAt, description);

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
                log.warn("OpenAI 응답에 choices가 없어 배너 AI 요약을 건너뜁니다. response={}", response);
                return null;
            }

            return response.choices().getFirst().message().content();
        } catch (Exception e) {
            // OpenAI 장애로 배너 등록 자체가 막히면 안 되므로 null로 폴백
            log.warn("배너 AI 요약 요청 실패, null로 폴백합니다.", e);
            return null;
        }
    }

    private record ChatRequest(String model, List<ChatMessage> messages) {}
    private record ChatMessage(String role, String content) {}
    private record ChatResponse(List<Choice> choices) {}
    private record Choice(ChatMessage message) {}
}