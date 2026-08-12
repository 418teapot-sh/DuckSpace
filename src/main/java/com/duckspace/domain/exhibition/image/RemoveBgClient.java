package com.duckspace.domain.exhibition.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * remove.bg 배경 제거 클라이언트.
 *
 * <p><b>무료 플랜은 월 50회, 0.25MP(preview) 로 제한됩니다.</b> {@code size} 를 {@code auto} 나
 * {@code full} 로 보내면 크레딧을 소모하며, 크레딧이 없으면 402 로 실패합니다.
 * 그래서 기본값이 {@code preview} 입니다.
 *
 * <p>키가 없으면 {@link #isEnabled()} 가 false 를 돌려주고, 호출부는 배경 제거를 건너뜁니다.
 * 키 없는 팀원도 나머지 기능을 개발할 수 있어야 하기 때문입니다.
 */
@Slf4j
@Component
public class RemoveBgClient {

    private static final String ENDPOINT = "https://api.remove.bg/v1.0/removebg";
    private static final String BOUNDARY = "----DuckSpaceBoundary";

    private final String apiKey;
    private final String size;
    private final HttpClient http;

    public RemoveBgClient(@Value("${removebg.api-key:}") String apiKey,
                          @Value("${removebg.size:preview}") String size) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.size = size;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        if (!isEnabled()) {
            log.warn("REMOVEBG_API_KEY 가 없어 배경 제거를 건너뜁니다. 업로드한 사진이 그대로 저장됩니다.");
        }
    }

    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    /**
     * 배경을 제거한 이미지를 반환합니다.
     *
     * @throws IOException API 호출이나 응답 해석에 실패한 경우
     */
    public BufferedImage removeBackground(byte[] imageBytes, String fileName)
            throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("X-Api-Key", apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofByteArray(buildBody(imageBytes, fileName)))
                .build();

        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());

        // 남은 호출량 파악에 필요합니다. 무료 플랜은 월 50회뿐이라 로그로 남겨둡니다.
        response.headers().firstValue("X-Credits-Charged")
                .ifPresent(charged -> log.info("remove.bg 크레딧 사용: {}", charged));

        if (response.statusCode() != 200) {
            throw new IOException("remove.bg 호출 실패 (HTTP %d): %s".formatted(
                    response.statusCode(), new String(response.body(), StandardCharsets.UTF_8)));
        }

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(response.body()));
        if (result == null) {
            throw new IOException("remove.bg 응답을 이미지로 읽지 못했습니다.");
        }
        return result;
    }

    private byte[] buildBody(byte[] imageBytes, String fileName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writeTextPart(out, "size", size);

        out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"image_file\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(imageBytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));

        out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void writeTextPart(ByteArrayOutputStream out, String name, String value) throws IOException {
        out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
