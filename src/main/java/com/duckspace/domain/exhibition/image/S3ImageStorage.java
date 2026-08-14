package com.duckspace.domain.exhibition.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

/**
 * S3 저장소.
 *
 * <p>EC2 에 붙은 IAM 역할을 그대로 씁니다. 액세스 키를 코드나 설정에 두지 않습니다
 * (SDK 의 기본 자격증명 체인이 인스턴스 메타데이터에서 알아서 가져옵니다).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3ImageStorage implements ImageStorage {

    /**
     * SDK 기본 타임아웃은 꽤 깁니다. {@code putObject} 는 스레드 둘짜리 이미지 실행기 안에서
     * 동기로 돌기 때문에, S3 가 느려지면 그 둘이 통째로 묶여 큐가 차고 업로드가 거절됩니다.
     * 오래 매달리기보다 빨리 실패하고 원본이라도 남기는 편이 낫습니다.
     */
    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(10);

    private final S3Client s3;
    private final String bucket;
    private final String publicBaseUrl;

    public S3ImageStorage(@Value("${storage.s3.bucket}") String bucket,
                          @Value("${storage.s3.region}") String region,
                          @Value("${storage.s3.public-base-url:}") String publicBaseUrl) {
        this.bucket = bucket;
        this.s3 = S3Client.builder()
                .region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(API_CALL_TIMEOUT)
                        .apiCallAttemptTimeout(API_CALL_ATTEMPT_TIMEOUT)
                        .build())
                .build();
        this.publicBaseUrl = publicBaseUrl.isBlank()
                ? "https://%s.s3.%s.amazonaws.com".formatted(bucket, region)
                : publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public String upload(String key, byte[] content, String contentType) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));

        log.debug("S3 업로드 완료: {}/{} ({} bytes)", bucket, key, content.length);
        return publicBaseUrl + "/" + key;
    }

    @Override
    public void deleteByUrl(String imageUrl) {
        String key = keyFrom(imageUrl);
        if (key == null) {
            return;
        }
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            log.debug("S3 삭제 완료: {}/{}", bucket, key);
        } catch (Exception e) {
            // 객체가 남는 것보다 삭제 요청이 실패하는 쪽이 사용자에게 더 나쁩니다.
            log.warn("S3 이미지 삭제 실패: {} ({})", key, e.toString());
        }
    }

    @Override
    public byte[] download(String imageUrl) {
        String key = keyFrom(imageUrl);
        if (key == null) {
            throw new UncheckedIOException(new IOException("이 버킷의 이미지가 아닙니다: " + imageUrl));
        }
        try {
            return s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
        } catch (Exception e) {
            throw new UncheckedIOException(new IOException("S3 이미지를 읽지 못했습니다: " + key, e));
        }
    }

    /** 우리가 만든 URL 이 아니면 건드리지 않습니다. */
    private String keyFrom(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(publicBaseUrl + "/")) {
            return null;
        }
        return imageUrl.substring(publicBaseUrl.length() + 1);
    }
}
