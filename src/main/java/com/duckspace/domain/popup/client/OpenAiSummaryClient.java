package com.duckspace.domain.popup.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

@Component
public class OpenAiSummaryClient {

    private static final String SYSTEM_PROMPT =
            "해당 클라이언트는 팝업스토어 일정을 10문장 이내로 정리해주는 어시스턴트야. 시작일과 종료일, 위치를 반드시 포함해서 안내해줘.";

    private final RestClient restClient;
    private final String model;

    public OpenAiSummaryClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model
    ) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer "+apiKey)
                .build();
    }

    public String summarizeSchedule(String title, String description, LocalDate startDate, LocalDate endDate) {
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


            return response.choices().get(0).message().content();
        } catch (Exception e) {
            // OpenAI 장애로 팝업 등록 자체가 막히면 안 되므로 null로 폴백
            return null;
        }
    }

    private record ChatRequest(String model, List<ChatMessage> messages) {}
    private record ChatMessage(String role, String content) {}
    private record ChatResponse(List<Choice> choices) {}
    private record Choice(ChatMessage message) {}
}





