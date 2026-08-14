package com.duckspace.domain.exhibition.image;

import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
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
@ConditionalOnProperty(name = "storage.type", havingValue = "local")
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
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return publicBaseUrl + "/" + key;
        } catch (IOException e) {
            throw new BusinessException(ExhibitionErrorCode.IMAGE_PROCESSING_FAILED);
        }
    }

    @Override
    public void deleteByUrl(String imageUrl) {
        String key = keyFrom(imageUrl);
        if (key == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            // 파일이 남는 것보다 삭제 요청이 실패하는 쪽이 사용자에게 더 나쁩니다.
            log.warn("로컬 이미지 삭제 실패: {} ({})", key, e.toString());
        }
    }

    @Override
    public byte[] download(String imageUrl) {
        String key = keyFrom(imageUrl);
        if (key == null) {
            throw new UncheckedIOException(new IOException("이 저장소의 이미지가 아닙니다: " + imageUrl));
        }
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code key} 에 {@code ../} 가 섞여 루트 밖으로 나가는 것을 막습니다. */
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ExhibitionErrorCode.IMAGE_PROCESSING_FAILED);
        }
        return target;
    }

    /** 우리가 만든 URL 이 아니면 건드리지 않습니다. */
    private String keyFrom(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(publicBaseUrl + "/")) {
            return null;
        }
        return imageUrl.substring(publicBaseUrl.length() + 1);
    }
}
