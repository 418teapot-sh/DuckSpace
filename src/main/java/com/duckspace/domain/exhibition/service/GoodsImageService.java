package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.dto.response.GoodsImagePageResponse;
import com.duckspace.domain.exhibition.dto.response.GoodsImageResponse;
import com.duckspace.domain.exhibition.entity.GoodsImage;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.domain.exhibition.image.ImageCleanup;
import com.duckspace.domain.exhibition.image.MultipartImageValidator;
import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import com.duckspace.domain.exhibition.repository.GoodsImageRepository;
import com.duckspace.global.exception.BusinessException;
import com.duckspace.global.support.Paging;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 굿즈 사진 보관함.
 *
 * <p>플로우: 사진 업로드 → 누끼·후처리(백그라운드) → 보관함에 READY 로 쌓임 →
 * 원하는 장식장에 배치. <b>배치는 기존 {@code POST /items}(imageUrl 직접)</b> 를 쓰므로
 * 여기에는 배치 로직이 없습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoodsImageService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final GoodsImageRepository goodsImageRepository;
    private final ExhibitionItemRepository exhibitionItemRepository;
    private final ImageCleanup imageCleanup;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 사진을 보관함에 접수합니다. <b>접수만 하고 즉시 응답합니다.</b>
     *
     * <p>굿즈 업로드와 달리 placement 를 받지 않습니다 — 배치는 나중에 보관함에서 골라서 합니다.
     */
    @Transactional
    public GoodsImageResponse upload(Long userId, MultipartFile image) {
        byte[] data = MultipartImageValidator.readBytes(image);
        MultipartImageValidator.validate(image, data);

        GoodsImage saved = goodsImageRepository.save(new GoodsImage(userId));

        String fileName = image.getOriginalFilename() == null ? "upload.png" : image.getOriginalFilename();
        eventPublisher.publishEvent(new GoodsImageUploadedEvent(saved.getId(), userId, data, fileName));

        return GoodsImageResponse.from(saved);
    }

    /** 폴링용 단건 조회. 본인 것만 볼 수 있습니다(남의 사진은 존재도 숨깁니다). */
    public GoodsImageResponse get(Long imageId, Long userId) {
        return GoodsImageResponse.from(getOwned(imageId, userId));
    }

    /** 보관함 목록. 최신순 커서 페이징이고, 본인 것이라 PENDING·FAILED 도 전부 나옵니다. */
    public GoodsImagePageResponse list(Long userId, Long cursor, Integer size) {
        int pageSize = Paging.normalize(size, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
        // 다음 페이지 존재 여부를 알기 위해 한 개 더 가져옵니다.
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<GoodsImage> found = (cursor == null)
                ? goodsImageRepository.findByUserIdOrderByIdDesc(userId, pageable)
                : goodsImageRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor, pageable);

        Paging.Slice<GoodsImage> sliced = Paging.slice(found, pageSize, GoodsImage::getId);
        return new GoodsImagePageResponse(
                sliced.page().stream().map(GoodsImageResponse::from).toList(),
                sliced.nextCursor(), sliced.hasNext());
    }

    /**
     * 실패한 사진을 다시 처리합니다. 재선택 없이 남겨둔 원본을 다시 태웁니다.
     * (락·방치 PENDING 허용·원본 없음 안내 — 굿즈 재시도와 같은 규칙)
     */
    @Transactional
    public GoodsImageResponse retry(Long imageId, Long userId) {
        GoodsImage image = goodsImageRepository.findOwnedForUpdate(imageId, userId)
                .orElseThrow(() -> new BusinessException(ExhibitionErrorCode.IMAGE_NOT_FOUND));

        if (image.getStatus() != ItemStatus.FAILED
                && !AbandonedPending.isAbandoned(image.getStatus(), image.getUpdatedAt())) {
            throw new BusinessException(ExhibitionErrorCode.IMAGE_NOT_RETRYABLE);
        }
        String source = image.getImageUrl();
        if (source == null || source.isBlank()) {
            throw new BusinessException(ExhibitionErrorCode.RETRY_SOURCE_MISSING);
        }

        image.markPending();
        // 이미 PENDING(방치)이었다면 markPending 이 no-op 이라 updatedAt 이 그대로 남고,
        // 그러면 계속 "방치됨" 으로 보여 연타마다 재처리가 중복 접수됩니다. 시계를 되감습니다.
        goodsImageRepository.touchUpdatedAt(image.getId(), LocalDateTime.now());
        eventPublisher.publishEvent(new GoodsImageRetryRequestedEvent(image.getId(), userId, source));

        return GoodsImageResponse.from(image);
    }

    /**
     * 보관함에서 사진을 삭제합니다.
     *
     * <p><b>장식장에 배치돼 있으면 409 로 막습니다.</b> 여기서 파일을 지우면 그 굿즈의 그림이
     * 통째로 깨집니다. 먼저 굿즈를 삭제(회수)한 뒤에 지울 수 있습니다.
     *
     * <p><b>남아 있는 창:</b> 배치 확인과 커밋 사이에 다른 요청이 같은 URL 을 장식장에 올리면,
     * 이 트랜잭션 스냅샷에는 안 보여서 409 없이 삭제가 진행됩니다. 그때도 <b>파일은
     * {@link ImageCleanup} 가드가 지켜주지만</b>(배치가 커밋됐으면 참조로 잡힘) 보관함 행은
     * 사라집니다 — 장식장에는 보이는데 보관함에는 없는 상태. 이걸 막으려면 존재 확인을
     * 잠금 읽기로 바꿔 {@code exhibition_item} 의 해당 URL 구간에 갭 락을 걸어야 하는데,
     * 배치 트래픽 전체와 락을 공유하게 되는 비용이 커서 여기서 멈췄습니다.
     */
    @Transactional
    public void delete(Long imageId, Long userId) {
        // retry() 와 같은 이유로 잠그고 읽습니다. 잠그지 않으면 같은 사진에 삭제가 두 번
        // 들어왔을 때 둘 다 통과하고, 뒤에 온 쪽의 엔티티 delete 가 이미 없는 행을 지우려다
        // ObjectOptimisticLockingFailureException 으로 500 이 됩니다(엔티티 삭제는 삭제된
        // 행 수를 항상 확인합니다 — 0 건이어도 조용히 넘어가지 않습니다).
        GoodsImage image = goodsImageRepository.findOwnedForUpdate(imageId, userId)
                .orElseThrow(() -> new BusinessException(ExhibitionErrorCode.IMAGE_NOT_FOUND));

        String url = image.getImageUrl();
        if (url != null && exhibitionItemRepository.existsByImageUrl(url)) {
            throw new BusinessException(ExhibitionErrorCode.IMAGE_IN_USE);
        }

        goodsImageRepository.delete(image);
        // PENDING 중 삭제라 url 이 없으면 지울 파일도 없습니다. 처리가 뒤늦게 끝나도
        // 상태 기록이 no-op 이 되면서 처리본은 파이프라인이 알아서 회수합니다.
        imageCleanup.deleteAfterCommit(url);
    }

    private GoodsImage getOwned(Long imageId, Long userId) {
        return goodsImageRepository.findById(imageId)
                .filter(found -> found.belongsTo(userId))
                .orElseThrow(() -> new BusinessException(ExhibitionErrorCode.IMAGE_NOT_FOUND));
    }

}
