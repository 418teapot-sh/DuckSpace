package com.duckspace.domain.exhibition.image;

import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 로컬 파일시스템 저장소.
 *
 * <p>개발자 PC 에는 EC2 와 달리 IAM 역할이 없어서 S3 에 바로 붙을 수 없습니다.
 * AWS 자격증명이 없어도 이미지 업로드 기능을 개발·테스트할 수 있도록 파일로 저장하고,
 * {@code /uploads/**} 로 서빙합니다. ({@link LocalStorageWebConfig})
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalImageStorage implements ImageStorage {

    private final Path root;
    private final String publicBaseUrl;

    public LocalImageStorage(@Value("${storage.local.directory:./uploads}") String directory,
                             @Value("${storage.local.public-base-url:http://localhost:8080/uploads}") String publicBaseUrl) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        log.info("로컬 이미지 저장소를 사용합니다: {}", root);
    }

    @Override
    public String upload(String key, byte[] content, String contentType) {
        try {
            Path target = root.resolve(key).normalize();
            if (!target.startsWith(root)) {
                // key 에 ../ 가 섞여 루트 밖으로 나가는 것을 막습니다.
                throw new BusinessException(ExhibitionErrorCode.IMAGE_PROCESSING_FAILED);
            }
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return publicBaseUrl + "/" + key;
        } catch (IOException e) {
            throw new BusinessException(ExhibitionErrorCode.IMAGE_PROCESSING_FAILED);
        }
    }
}
