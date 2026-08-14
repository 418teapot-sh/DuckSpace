package com.duckspace.domain.exhibition.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 로컬 저장소에 올린 이미지를 {@code /uploads/**} 로 서빙합니다. 로컬 개발 전용입니다. */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "local")
public class LocalStorageWebConfig implements WebMvcConfigurer {

    private final String directory;

    public LocalStorageWebConfig(@Value("${storage.local.directory:./uploads}") String directory) {
        this.directory = directory;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Path.of(directory).toAbsolutePath().normalize();

        // 디렉토리가 아직 없으면 Path.toUri() 가 끝에 "/" 를 붙이지 않습니다(디렉토리인 줄 모르므로).
        // 슬래시가 없는 location 은 디렉토리가 아니라 파일 하나로 취급돼서, 그 뒤 요청이 전부 404 가
        // 됩니다. 첫 업로드로 디렉토리가 생겨도 이 설정은 부팅 때 이미 굳은 뒤라 재시작 전까지
        // 복구되지 않습니다. 그래서 미리 만들고, 슬래시도 직접 붙입니다.
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            log.warn("업로드 디렉토리를 만들지 못했습니다: {} ({})", root, e.toString());
        }

        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
