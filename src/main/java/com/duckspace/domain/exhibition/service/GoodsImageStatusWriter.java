package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.entity.GoodsImage;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import com.duckspace.domain.exhibition.repository.GoodsImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 보관함 사진의 처리 결과만 <b>짧은 트랜잭션</b>으로 기록합니다.
 *
 * <p>{@link ExhibitionItemStatusWriter} 와 대상만 다르고 규칙은 같습니다 —
 * 트랜잭션이 외부 API 왕복을 물지 않도록 결과 기록만 분리하고, <b>{@code PENDING} 인
 * 행에만</b> 씁니다(늦게 끝난 작업이 먼저 끝난 결과를 덮지 못하게).
 *
 * <p>반환값 {@code false} 는 "기록할 자격이 없었다"(삭제됨 · 이미 처리 끝남)는 뜻이라,
 * 호출부는 방금 올린 이미지를 회수해야 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class GoodsImageStatusWriter {

    private final GoodsImageRepository goodsImageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markReady(Long imageId, String imageUrl) {
        return pending(imageId).map(image -> {
            image.markReady(imageUrl);
            return true;
        }).orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(Long imageId, String originalImageUrl) {
        return pending(imageId).map(image -> {
            image.markFailed(originalImageUrl);
            return true;
        }).orElse(false);
    }

    private Optional<GoodsImage> pending(Long imageId) {
        Optional<GoodsImage> found = goodsImageRepository.findById(imageId);
        if (found.isEmpty()) {
            log.info("상태를 기록할 보관함 사진이 이미 없습니다. imageId={}", imageId);
            return Optional.empty();
        }
        if (found.get().getStatus() != ItemStatus.PENDING) {
            log.info("이미 처리가 끝난 사진이라 결과를 버립니다. imageId={}, status={}",
                    imageId, found.get().getStatus());
            return Optional.empty();
        }
        return found;
    }
}
