package com.duckspace.domain.banner.client;

import com.duckspace.global.support.openai.AbstractOpenAiSummaryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class BannerSummaryClient extends AbstractOpenAiSummaryClient {

    private static final String SYSTEM_PROMPT =
            "해당 클라이언트는 배너가 홍보하는 이벤트 내용을 10문장 이내로 정리해주는 어시스턴트야. "
                    + "핵심 내용과 노출 기간을 반드시 포함해서 안내해줘.";

    public BannerSummaryClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model
    ) {
        super(apiKey, model);
    }

    public String summarizeBanner(String title, String description, LocalDateTime startAt, LocalDateTime endAt) {
        String userContent = """
                제목: %s
                노출 기간: %s ~ %s
                설명: %s
                """.formatted(title, startAt, endAt, description);

        return summarize(SYSTEM_PROMPT, userContent, "배너");
    }
}
