package com.duckspace.global.support.openai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 키가 없을 때 <b>외부 호출로 안 나가는지</b>를 고정합니다.
 *
 * <p>테스트 설정({@code application-test.yml})이 이 게이트에 의존합니다 — 예전에는 키가
 * {@code test-dummy-key} 로 채워져 있어서 활성으로 판정됐고, 테스트를 돌릴 때마다
 * api.openai.com 으로 실제 요청이 나갔습니다. 401 을 받고 null 로 폴백되니 통과는 했지만,
 * 테스트가 외부 네트워크에 의존하는 상태였습니다.
 *
 * <p>여기서 실제 호출이 일어나면 네트워크가 없는 CI 에서 이 테스트가 느려지거나 실패합니다 —
 * 그게 곧 게이트가 깨졌다는 신호입니다.
 */
class AbstractOpenAiSummaryClientTest {

    private static class TestClient extends AbstractOpenAiSummaryClient {

        TestClient(String apiKey) {
            super(apiKey, "gpt-4o-mini");
        }

        String call() {
            return summarize("시스템 프롬프트", "요약할 내용", "테스트");
        }
    }

    @Test
    @DisplayName("키가 없으면 호출하지 않고 null 을 돌려준다")
    void 키가_없으면_건너뛴다() {
        assertThat(new TestClient(null).call()).isNull();
        assertThat(new TestClient("").call()).isNull();
        assertThat(new TestClient("   ").call())
                .as("공백만 있는 값도 '키 없음' 입니다")
                .isNull();
    }
}
