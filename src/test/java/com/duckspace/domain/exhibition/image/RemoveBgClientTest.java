package com.duckspace.domain.exhibition.image;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 진짜 remove.bg 를 부르지 않고 로컬 {@link HttpServer} 스텁으로 키 로테이션 분기를 검증합니다.
 * (Spring 컨텍스트 없이 생성자를 직접 호출하는 순수 단위 테스트 — MySQL 불필요)
 */
class RemoveBgClientTest {

    private HttpServer server;
    private final Deque<Integer> statusQueue = new ArrayDeque<>();
    private final List<String> requestedKeys = new CopyOnWriteArrayList<>();
    private byte[] successBody;

    @BeforeEach
    void setUp() throws IOException {
        successBody = pngBytes();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/removebg", exchange -> {
            requestedKeys.add(exchange.getRequestHeaders().getFirst("X-Api-Key"));
            exchange.getRequestBody().readAllBytes();

            Integer status = statusQueue.pollFirst();
            if (status == null) {
                status = 500;
            }
            byte[] body = (status == 200) ? successBody : "stub error".getBytes();

            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private RemoveBgClient client(String... keys) {
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/removebg";
        return new RemoveBgClient(String.join(",", keys), "", "preview", endpoint,
                RemoveBgClient.TOTAL_BUDGET_SECONDS);
    }

    @Test
    @DisplayName("첫 키가 402면 다음 키로 재시도한다")
    void rotatesToNextKeyOn402() throws Exception {
        statusQueue.add(402);
        statusQueue.add(200);
        RemoveBgClient client = client("key-a", "key-b");

        BufferedImage result = client.removeBackground(new byte[]{1, 2, 3}, "a.png");

        assertThat(result).isNotNull();
        assertThat(requestedKeys).containsExactly("key-a", "key-b");
    }

    @Test
    @DisplayName("등록된 키를 전부 402로 소진하면 예외를 던진다")
    void throwsWhenAllKeysExhausted() {
        statusQueue.add(402);
        statusQueue.add(402);
        RemoveBgClient client = client("key-a", "key-b");

        assertThatThrownBy(() -> client.removeBackground(new byte[]{1, 2, 3}, "a.png"))
                .isInstanceOf(IOException.class);
        assertThat(requestedKeys).containsExactly("key-a", "key-b");
    }

    @Test
    @DisplayName("400은 로테이션하지 않고 즉시 실패한다 — 다른 키를 헛되이 태우지 않는다")
    void doesNotRotateOnNonRotatableStatus() {
        statusQueue.add(400);
        RemoveBgClient client = client("key-a", "key-b");

        assertThatThrownBy(() -> client.removeBackground(new byte[]{1, 2, 3}, "a.png"))
                .isInstanceOf(IOException.class);
        assertThat(requestedKeys).containsExactly("key-a");
    }

    @Test
    @DisplayName("403도 다음 키로 전환한다")
    void rotatesToNextKeyOn403() throws Exception {
        statusQueue.add(403);
        statusQueue.add(200);
        RemoveBgClient client = client("key-a", "key-b");

        BufferedImage result = client.removeBackground(new byte[]{1, 2, 3}, "a.png");

        assertThat(result).isNotNull();
        assertThat(requestedKeys).containsExactly("key-a", "key-b");
    }

    @Test
    @DisplayName("성공 응답은 픽셀 수 가드를 거쳐 이미지로 디코딩된다")
    void decodesSuccessfulResponse() throws Exception {
        statusQueue.add(200);
        RemoveBgClient client = client("key-a");

        BufferedImage result = client.removeBackground(new byte[]{1, 2, 3}, "a.png");

        assertThat(result.getWidth()).isEqualTo(10);
        assertThat(result.getHeight()).isEqualTo(10);
    }

    @Test
    @DisplayName("총 예산을 넘기면 남은 키를 태우지 않는다")
    void stopsRotatingWhenTotalBudgetExhausted() {
        // 타임아웃은 시도마다 새로 걸려서, 예산이 없으면 최악이 키 개수에 비례합니다
        // (키 6개면 420초). 그 사이 배포가 시작되면 systemd 가 SIGKILL 을 보내
        // 처리 중이던 사진이 깨집니다 — IMAGE_AWAIT_SECONDS 가 막으려던 상황입니다.
        // 예산을 0 으로 두면 첫 시도 직후 마감이라, 두 번째 키로 넘어가지 않아야 합니다.
        statusQueue.add(402);
        statusQueue.add(200);
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/removebg";
        RemoveBgClient client = new RemoveBgClient("key-a,key-b", "", "preview", endpoint, 0);

        assertThatThrownBy(() -> client.removeBackground(new byte[]{1, 2, 3}, "a.png"))
                .isInstanceOf(IOException.class);

        assertThat(requestedKeys)
                .as("예산이 없으면 첫 키만 시도하고 멈춰야 합니다")
                .containsExactly("key-a");
    }

    @Test
    @DisplayName("새 이름이 비어 있으면 예전 이름(REMOVEBG_API_KEY)으로 동작한다")
    void fallsBackToLegacyKeyName() throws Exception {
        // 이름이 안 옮겨진 환경에서 배경 제거가 조용히 꺼지는 것을 막는 폴백입니다.
        statusQueue.add(200);
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/removebg";
        RemoveBgClient client = new RemoveBgClient("", "legacy-key", "preview", endpoint,
                RemoveBgClient.TOTAL_BUDGET_SECONDS);

        assertThat(client.isEnabled()).isTrue();
        client.removeBackground(new byte[]{1, 2, 3}, "a.png");

        assertThat(requestedKeys).containsExactly("legacy-key");
    }

    @Test
    @DisplayName("새 이름이 있으면 예전 이름을 무시한다")
    void newNameWinsOverLegacyName() throws Exception {
        // 우선순위가 뒤집히면 새로 넣은 키들을 두고 소진된 옛 키만 씁니다 — 그런데
        // 로그는 정상이고 응답도 200 이라 눈에 안 띕니다.
        statusQueue.add(200);
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/removebg";
        new RemoveBgClient("new-key", "legacy-key", "preview", endpoint,
                RemoveBgClient.TOTAL_BUDGET_SECONDS)
                .removeBackground(new byte[]{1, 2, 3}, "a.png");

        assertThat(requestedKeys).containsExactly("new-key");
    }

    private static byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
