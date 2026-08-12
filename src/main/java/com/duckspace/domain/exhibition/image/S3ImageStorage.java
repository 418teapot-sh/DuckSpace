package com.duckspace.domain.exhibition.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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

    private final S3Client s3;
    private final String bucket;
    private final String publicBaseUrl;

    public S3ImageStorage(@Value("${storage.s3.bucket}") String bucket,
                          @Value("${storage.s3.region}") String region,
                          @Value("${storage.s3.public-base-url:}") String publicBaseUrl) {
        this.bucket = bucket;
        this.s3 = S3Client.builder().region(Region.of(region)).build();
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
}
