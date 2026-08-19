package com.duckspace.global.support.openai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Slf4j
public abstract class AbstractOpenAiSummaryClient {

    private final RestClient restClient;
    private final String model;
    private final boolean enabled;

    /** 연결이 안 잡히는 것은 빨리 포기합니다. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 응답 대기 상한.
     *
     * <p><b>이 값이 없으면 무한입니다.</b> {@code RestClient.builder()} 는 Boot 가 구성해주는
     * {@code RestClient.Builder} 빈이 아니라 정적 팩토리라 {@code spring.http.client.*} 설정을
     * 받지 않고, 그 아래 JDK 클라이언트는 read timeout 기본값이 없습니다.
     *
     * <p>요약 호출은 {@code @Transactional} 안에서 일어납니다. 그래서 OpenAI 가 응답을 주지
     * 않으면 <b>톰캣 워커와 DB 커넥션을 무기한 붙잡습니다.</b> "실패하면 null 로 폴백" 하는
     * catch 는 <b>행(hang)에는 걸리지 않습니다</b> — 예외가 아니니까요. 커넥션 풀이 먼저 마릅니다.
     */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    protected AbstractOpenAiSummaryClient(String apiKey, String model) {
        this.model = model;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.restClient = RestClient.builder()
                .requestFactory(timeoutAwareRequestFactory())
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    /**
     * 같은 JDK 클라이언트를 쓰되 타임아웃만 명시합니다. 구현체를 바꾸지 않으려는 선택입니다 —
     * 여기서 팩토리 종류까지 바꾸면 타임아웃 말고 다른 동작도 같이 달라집니다.
     */
    private static ClientHttpRequestFactory timeoutAwareRequestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
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
