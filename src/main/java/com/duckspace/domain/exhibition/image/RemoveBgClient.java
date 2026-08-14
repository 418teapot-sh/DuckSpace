package com.duckspace.domain.exhibition.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

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

    /** 파일명에 허용할 문자. 나머지는 전부 {@code _} 로 바꿉니다. */
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[^A-Za-z0-9._-]");
    private static final int MAX_FILENAME_LENGTH = 100;
    private static final String FALLBACK_FILENAME = "upload.png";

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

        // boundary 를 요청마다 새로 뽑습니다. 고정값이면 업로드된 바이트 안에 같은 문자열을
        // 심어 파트 경계를 위조할 수 있습니다.
        String boundary = "----DuckSpace" + UUID.randomUUID().toString().replace("-", "");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("X-Api-Key", apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofByteArray(buildBody(boundary, imageBytes, fileName)))
                .build();

        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());

        // 남은 호출량 파악에 필요합니다. 무료 플랜은 월 50회뿐이라 로그로 남겨둡니다.
        response.headers().firstValue("X-Credits-Charged")
                .ifPresent(charged -> log.info("remove.bg 크레딧 사용: {}", charged));

        if (response.statusCode() != 200) {
            throw new IOException("remove.bg 호출 실패 (HTTP %d): %s".formatted(
                    response.statusCode(), new String(response.body(), StandardCharsets.UTF_8)));
        }

        // 응답도 픽셀 수 제한을 거쳐 디코딩합니다. 우리가 부른 API 라도 가드를 건너뛰면,
        // 이쪽이 실사용 경로(키가 있는 배포 환경)라서 보호가 사실상 없는 것과 같습니다.
        try {
            return ImageInspector.read(response.body());
        } catch (UncheckedIOException e) {
            throw new IOException("remove.bg 응답을 이미지로 읽지 못했습니다.", e.getCause());
        }
    }

    private byte[] buildBody(String boundary, byte[] imageBytes, String fileName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writeTextPart(out, boundary, "size", size);

        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"image_file\"; filename=\"%s\"\r\n"
                .formatted(sanitizeFileName(fileName))).getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(imageBytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));

        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /**
     * 파일명을 헤더에 넣어도 안전한 형태로 깎습니다.
     *
     * <p><b>사용자가 올린 파일명이 그대로 헤더에 들어가던 자리입니다.</b> CRLF 뿐 아니라
     * RFC 2231 인코딩({@code filename*=UTF-8''%0d%0a...})으로도 파트를 새로 주입할 수 있어서,
     * 개별 문자를 이스케이프하는 대신 <b>허용 문자만 남기는</b> 방식으로 막습니다.
     * 파일명은 remove.bg 가 로그에나 쓰는 값이라 깎여도 잃는 것이 없습니다.
     */
    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return FALLBACK_FILENAME;
        }
        String safe = UNSAFE_FILENAME_CHARS.matcher(fileName).replaceAll("_");
        if (safe.length() > MAX_FILENAME_LENGTH) {
            safe = safe.substring(0, MAX_FILENAME_LENGTH);
        }
        // "..", "." 처럼 남은 것이 경로 조각뿐이면 쓸 이유가 없습니다.
        return safe.replace(".", "").isBlank() ? FALLBACK_FILENAME : safe;
    }

    private static void writeTextPart(ByteArrayOutputStream out, String boundary,
                                      String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
