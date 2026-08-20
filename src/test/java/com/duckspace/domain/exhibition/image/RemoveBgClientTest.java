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
        return new RemoveBgClient(String.join(",", keys), "", "preview", endpoint);
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

    private static byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
