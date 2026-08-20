package com.duckspace.domain.exhibition.image;

import jakarta.annotation.PreDestroy;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * remove.bg 배경 제거 클라이언트.
 *
 * <p><b>무료 플랜은 계정당 월 50회, 0.25MP(preview) 로 제한됩니다.</b> {@code size} 를 {@code auto} 나
 * {@code full} 로 보내면 크레딧을 소모하며, 크레딧이 없으면 402 로 실패합니다.
 * 그래서 기본값이 {@code preview} 입니다.
 *
 * <p>서로 다른 계정에서 발급받은 키를 여러 개({@code removebg.api-keys}) 등록할 수 있습니다.
 * 한 키가 크레딧을 소진(402)하거나 무효(403)해지면 다음 키로 자동 전환합니다. 그 외 상태코드는
 * 로테이션 대상이 아닙니다 — 다른 키로 바꿔도 풀리지 않는 문제라서 즉시 실패시킵니다.
 *
 * <p>키가 없으면 {@link #isEnabled()} 가 false 를 돌려주고, 호출부는 배경 제거를 건너뜁니다.
 * 키 없는 팀원도 나머지 기능을 개발할 수 있어야 하기 때문입니다.
 */
@Slf4j
@Component
public class RemoveBgClient {

    /**
     * 테스트에서 {@code removebg.endpoint} 로 로컬 스텁 서버를 대신 붙일 수 있도록 설정값으로
     * 뺐습니다. 실제 remove.bg 를 부르지 않고는 로테이션 분기(402/403 전환, 다른 코드는
     * 즉시 실패)를 검증할 방법이 없었기 때문입니다.
     */
    private static final String DEFAULT_ENDPOINT = "https://api.remove.bg/v1.0/removebg";

    /** 파일명에 허용할 문자. 나머지는 전부 {@code _} 로 바꿉니다. */
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[^A-Za-z0-9._-]");
    private static final int MAX_FILENAME_LENGTH = 100;
    private static final String FALLBACK_FILENAME = "upload.png";

    /**
     * 에러 응답 본문을 로그에 남길 때의 상한.
     *
     * <p>이 메시지는 {@code ExhibitionImageProcessor} 가 {@code e.toString()} 으로 찍습니다.
     * 프록시가 잘못 끼어 HTML 안내 페이지가 오면 수백 KB 가 로그에 통째로 들어갑니다.
     * 원인 파악에는 앞부분이면 충분합니다.
     */
    private static final int ERROR_BODY_LOG_LIMIT = 512;

    private final List<String> apiKeys;
    private final String size;
    private final String endpoint;
    private final HttpClient http;

    /** 다음 호출에 쓸 키의 인덱스. 402/403 을 받으면 다음 키로 넘어갑니다. */
    private final AtomicInteger currentIndex = new AtomicInteger(0);

    public RemoveBgClient(@Value("${removebg.api-keys:}") String apiKeysRaw,
                          // removebg.api-key 는 removebg.api-keys 로 이름이 바뀌었습니다. 옛날 이름만
                          // 설정된 환경(서버 app.env, 팀원 로컬 .env)이 배경 제거가 조용히 꺼지는 걸
                          // 막기 위한 폴백입니다. 모든 환경이 새 이름으로 옮겨가면 지워도 됩니다.
                          @Value("${removebg.api-key:}") String legacySingleKey,
                          @Value("${removebg.size:preview}") String size,
                          @Value("${removebg.endpoint:" + DEFAULT_ENDPOINT + "}") String endpoint) {
        String rawKeys = apiKeysRaw == null ? "" : apiKeysRaw;
        String legacyKey = legacySingleKey == null ? "" : legacySingleKey;
        String effectiveRaw = rawKeys.isBlank() ? legacyKey : rawKeys;

        if (rawKeys.isBlank() && !legacyKey.isBlank()) {
            log.warn("removebg.api-key(REMOVEBG_API_KEY) 는 removebg.api-keys(REMOVEBG_API_KEYS) 로 "
                    + "바뀌었습니다. 예전 이름으로 동작 중이니 옮겨주세요.");
        }

        this.apiKeys = Arrays.stream(effectiveRaw.split(","))
                .map(String::trim)
                .filter(key -> !key.isBlank())
                .toList();
        this.size = size;
        this.endpoint = endpoint;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        if (!isEnabled()) {
            log.warn("REMOVEBG_API_KEYS 가 없어 배경 제거를 건너뜁니다. 업로드한 사진이 그대로 저장됩니다.");
        }
    }

    public boolean isEnabled() {
        return !apiKeys.isEmpty();
    }

    /**
     * 배경을 제거한 이미지를 반환합니다.
     *
     * <p>키가 여러 개 등록돼 있으면 402(크레딧 소진)나 403(인증 실패)을 받을 때마다 다음 키로
     * 넘어가 재시도합니다. 등록된 키를 전부 소진/실패하면 예외를 던지고, 호출부
     * ({@code ExhibitionImageProcessor})가 원본 이미지로 폴백합니다.
     *
     * @throws IOException API 호출이나 응답 해석에 실패한 경우 (모든 키 소진 포함)
     */
    public BufferedImage removeBackground(byte[] imageBytes, String fileName)
            throws IOException, InterruptedException {

        if (apiKeys.isEmpty()) {
            throw new IOException("등록된 remove.bg 키가 없습니다.");
        }

        // boundary 를 요청마다 새로 뽑습니다. 고정값이면 업로드된 바이트 안에 같은 문자열을
        // 심어 파트 경계를 위조할 수 있습니다. 키를 바꿔 재시도해도 바디는 동일하므로 한 번만 만듭니다.
        String boundary = "----DuckSpace" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildBody(boundary, imageBytes, fileName);

        IOException lastFailure = null;

        for (int attempt = 0; attempt < apiKeys.size(); attempt++) {
            int keyIndex = currentIndex.get();
            String key = apiKeys.get(keyIndex);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("X-Api-Key", key)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());

            // 남은 호출량 파악에 필요합니다. 무료 플랜은 계정당 월 50회뿐이라 로그로 남겨둡니다.
            response.headers().firstValue("X-Credits-Charged")
                    .ifPresent(charged -> log.info("remove.bg 키 #{} 크레딧 사용: {}", keyIndex, charged));

            if (response.statusCode() == 200) {
                // 응답도 픽셀 수 제한을 거쳐 디코딩합니다. 우리가 부른 API 라도 가드를 건너뛰면,
                // 이쪽이 실사용 경로(키가 있는 배포 환경)라서 보호가 사실상 없는 것과 같습니다.
                try {
                    return ImageInspector.read(response.body());
                } catch (UncheckedIOException e) {
                    throw new IOException("remove.bg 응답을 이미지로 읽지 못했습니다.", e.getCause());
                }
            }

            if (response.statusCode() == 402) {
                log.info("remove.bg 키 #{} 크레딧 소진, 다음 키로 전환합니다.", keyIndex);
                currentIndex.compareAndSet(keyIndex, (keyIndex + 1) % apiKeys.size());
                lastFailure = new IOException("remove.bg 키 #%d 크레딧 소진".formatted(keyIndex));
                continue;
            }

            if (response.statusCode() == 403) {
                log.error("remove.bg 키 #{} 인증 실패(오타/만료/정지 의심) — 키 확인이 필요합니다. 다음 키로 전환합니다.",
                        keyIndex);
                currentIndex.compareAndSet(keyIndex, (keyIndex + 1) % apiKeys.size());
                lastFailure = new IOException("remove.bg 키 #%d 인증 실패".formatted(keyIndex));
                continue;
            }

            // 429(분당 요청 한도 초과)도 여기로 떨어져 로테이션하지 않습니다. remove.bg 가 이
            // 한도를 키 단위로 매기는지 IP 단위로 매기는지 확인하지 못했습니다 — IP 단위라면
            // 키를 바꿔도 안 풀리고, 키 단위라면 402/403 처럼 로테이션 대상이어야 합니다.
            // 확인 전까지는 보수적으로 즉시 실패시킵니다.
            throw new IOException("remove.bg 호출 실패 (HTTP %d): %s".formatted(
                    response.statusCode(), summarize(response.body())));
        }

        throw lastFailure;
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

    /** 에러 본문을 로그에 넣을 만큼만 잘라냅니다. */
    private static String summarize(byte[] responseBody) {
        String text = new String(responseBody, StandardCharsets.UTF_8);
        if (text.length() <= ERROR_BODY_LOG_LIMIT) {
            return text;
        }
        return text.substring(0, ERROR_BODY_LOG_LIMIT) + "... (%d바이트 중 앞부분)".formatted(responseBody.length);
    }

    /**
     * {@link HttpClient} 는 셀렉터 스레드와 커넥션 풀을 들고 있습니다. 운영에서는 싱글턴이라
     * 한 번이지만, 테스트는 스프링 컨텍스트가 새로 뜰 때마다 이걸 하나씩 더 만들어서
     * JVM 이 끝날 때까지 쌓입니다.
     */
    @PreDestroy
    public void close() {
        http.close();
    }
}
