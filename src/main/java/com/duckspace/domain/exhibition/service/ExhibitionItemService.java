package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.dto.request.AddItemRequest;
import com.duckspace.domain.exhibition.dto.request.UpdatePositionRequest;
import com.duckspace.domain.exhibition.dto.request.UploadItemRequest;
import com.duckspace.domain.exhibition.dto.response.ExhibitionItemPageResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionItemResponse;
import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.domain.exhibition.image.ImageCleanup;
import com.duckspace.domain.exhibition.image.ImageInspector;
import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import com.duckspace.global.exception.BusinessException;
import com.duckspace.global.support.Paging;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExhibitionItemService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 이보다 오래 PENDING 인 아이템은 처리가 끊긴 것으로 보고 재시도를 허용합니다. */
    private static final Duration ABANDONED_PENDING_THRESHOLD = Duration.ofMinutes(15);

    /** 브라우저가 보내는 이미지 MIME 타입. ImageIO 가 읽을 수 있는 형식으로 제한합니다. */
    private static final Set<String> SUPPORTED_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

    private final ExhibitionItemRepository exhibitionItemRepository;
    private final ExhibitionService exhibitionService;
    private final ImageCleanup imageCleanup;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 장식장에 굿즈를 배치합니다. 이미지 주소를 이미 알고 있을 때 쓰는 경로입니다.
     *
     * <p>자유 배치라 위치가 겹쳐도 막지 않습니다. 겹쳐 놓는 연출도 사용자의 선택입니다.
     */
    @Transactional
    public ExhibitionItemResponse add(Long exhibitionId, Long userId, AddItemRequest request) {
        Exhibition exhibition = exhibitionService.getOwnedExhibition(exhibitionId, userId);

        ExhibitionItem item = new ExhibitionItem(
                exhibition, request.placement().toPlacement(), request.imageUrl(),
                request.itemName(), request.price(), request.comment(), ItemStatus.READY);

        return ExhibitionItemResponse.from(exhibitionItemRepository.save(item));
    }

    /**
     * 사진을 올려 배치합니다. <b>접수만 하고 즉시 응답합니다.</b>
     *
     * <p>배경 제거와 후처리는 뒤에서 돌아가므로 응답 시점의 상태는 {@link ItemStatus#PENDING} 입니다.
     * 프론트는 응답의 {@code itemId} 로 상태를 폴링해 {@code READY} 가 되면 화면에 반영하세요.
     */
    @Transactional
    public ExhibitionItemResponse upload(Long exhibitionId, Long userId,
                                          MultipartFile image, UploadItemRequest request) {
        Exhibition exhibition = exhibitionService.getOwnedExhibition(exhibitionId, userId);

        // 행을 만들기 전에 바이트까지 확인합니다. 나중에 걸러내면 PENDING 인 껍데기가 남습니다.
        byte[] data = readBytes(image);
        validateImage(image, data);

        ExhibitionItem item = new ExhibitionItem(
                exhibition, request.placement().toPlacement(), null,
                request.itemName(), request.price(), request.comment(), ItemStatus.PENDING);

        ExhibitionItem saved = exhibitionItemRepository.save(item);

        String fileName = image.getOriginalFilename() == null ? "upload.png" : image.getOriginalFilename();
        eventPublisher.publishEvent(
                new ItemImageUploadedEvent(saved.getId(), exhibition.getId(), data, fileName));

        return ExhibitionItemResponse.from(saved);
    }

    /**
     * 폴링용 단건 조회. 그리드 전체를 다시 받지 않고 상태만 확인할 때 씁니다.
     *
     * <p>목록과 <b>같은 기준으로</b> 가립니다. 여기만 열어두면 남의 장식장에서 처리 중이거나
     * 실패한 굿즈의 원본 이미지 주소를 그대로 볼 수 있습니다.
     */
    public ExhibitionItemResponse get(Long exhibitionId, Long itemId, Long viewerId) {
        Exhibition exhibition = exhibitionService.getExhibition(exhibitionId);
        ExhibitionItem item = getItemOf(exhibitionId, itemId);

        if (!ItemStatus.visibleTo(exhibition.isOwnedBy(viewerId)).contains(item.getStatus())) {
            throw new BusinessException(ExhibitionErrorCode.ITEM_NOT_FOUND);
        }
        return ExhibitionItemResponse.from(item);
    }

    /**
     * 실패한 굿즈를 다시 처리합니다. 사진을 다시 고르지 않아도 됩니다.
     *
     * <p>실패했을 때 원본을 저장소에 남겨두므로 그걸 다시 태웁니다. 다만 <b>원본조차 저장하지
     * 못한 경우</b>(저장소 자체가 죽었거나 큐가 가득 차 접수 단계에서 실패한 경우)에는 남은 것이
     * 없어서, 삭제 후 다시 올리는 수밖에 없습니다. 그 경우를 따로 알려줍니다.
     *
     * <p>다운로드와 재처리는 {@link ExhibitionImageProcessor} 가 커밋 이후 백그라운드에서 합니다.
     */
    @Transactional
    public ExhibitionItemResponse retry(Long exhibitionId, Long itemId, Long userId) {
        exhibitionService.getOwnedExhibition(exhibitionId, userId);

        // 행을 잠그고 읽습니다. 잠그지 않으면 빠르게 두 번 누른 요청이 둘 다 FAILED 를 보고
        // 통과해서, 같은 사진이 두 번 처리되고 remove.bg 크레딧도 두 번 나갑니다.
        ExhibitionItem item = exhibitionItemRepository.findByIdForUpdate(itemId)
                .filter(found -> found.belongsTo(exhibitionId))
                .orElseThrow(() -> new BusinessException(ExhibitionErrorCode.ITEM_NOT_FOUND));

        if (item.getStatus() != ItemStatus.FAILED && !isAbandonedPending(item)) {
            // 뒤에 온 요청은 앞 요청이 PENDING 으로 바꿔둔 것을 보고 여기서 물러납니다.
            throw new BusinessException(ExhibitionErrorCode.ITEM_NOT_RETRYABLE);
        }
        String source = item.getImageUrl();
        if (source == null || source.isBlank()) {
            throw new BusinessException(ExhibitionErrorCode.RETRY_SOURCE_MISSING);
        }

        item.markPending();
        eventPublisher.publishEvent(
                new ItemImageRetryRequestedEvent(item.getId(), exhibitionId, source));

        return ExhibitionItemResponse.from(item);
    }

    /**
     * 강제 종료(OOM 등)로 처리가 끊긴 채 방치된 {@code PENDING} 인지.
     *
     * <p>정상 종료는 처리 완료를 기다리지만 프로세스가 그냥 죽으면 {@code PENDING} 이
     * 영원히 남는데, 재시도가 {@code FAILED} 만 받으면 사용자가 복구할 방법이 없습니다.
     * 그래서 <b>오래 방치된 PENDING 은 실패한 것으로 간주</b>하고 재시도를 허용합니다.
     *
     * <p>기준을 넉넉히 잡은 이유: 처리 큐가 꽉 찼을 때 최악 대기가 10분을 넘을 수 있습니다
     * (큐 20 x remove.bg 타임아웃 60초 / 스레드 2). 아직 살아있는 작업과 겹치더라도,
     * 결과 기록은 PENDING 가드가 선착순으로 지키고 늦은 쪽의 업로드는 회수되므로
     * 낭비일 뿐 데이터가 깨지지는 않습니다.
     */
    private static boolean isAbandonedPending(ExhibitionItem item) {
        return item.getStatus() == ItemStatus.PENDING
                && item.getUpdatedAt() != null
                && item.getUpdatedAt().isBefore(LocalDateTime.now().minus(ABANDONED_PENDING_THRESHOLD));
    }

    /** 드래그 이동·크기 조절 결과를 저장합니다. */
    @Transactional
    public ExhibitionItemResponse updatePosition(Long exhibitionId, Long itemId, Long userId,
                                                  UpdatePositionRequest request) {
        exhibitionService.getOwnedExhibition(exhibitionId, userId);
        ExhibitionItem item = getItemOf(exhibitionId, itemId);

        item.moveTo(request.placement().toPlacement());
        return ExhibitionItemResponse.from(item);
    }

    /** 전시된 굿즈 그리드. 최신순 커서 페이징입니다. */
    public ExhibitionItemPageResponse list(Long exhibitionId, Long viewerId, Long cursor, Integer size) {
        Exhibition exhibition = exhibitionService.getExhibition(exhibitionId);
        Set<ItemStatus> visible = ItemStatus.visibleTo(exhibition.isOwnedBy(viewerId));

        int pageSize = Paging.normalize(size, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
        // 다음 페이지 존재 여부를 알기 위해 한 개 더 가져옵니다.
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<ExhibitionItem> found = (cursor == null)
                ? exhibitionItemRepository.findByExhibitionIdAndStatusInOrderByIdDesc(
                        exhibitionId, visible, pageable)
                : exhibitionItemRepository.findByExhibitionIdAndStatusInAndIdLessThanOrderByIdDesc(
                        exhibitionId, visible, cursor, pageable);

        boolean hasNext = found.size() > pageSize;
        List<ExhibitionItem> page = hasNext ? found.subList(0, pageSize) : found;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        return new ExhibitionItemPageResponse(
                page.stream().map(ExhibitionItemResponse::from).toList(), nextCursor, hasNext);
    }

    @Transactional
    public void delete(Long exhibitionId, Long itemId, Long userId) {
        exhibitionService.getOwnedExhibition(exhibitionId, userId);

        ExhibitionItem item = getItemOf(exhibitionId, itemId);
        exhibitionItemRepository.delete(item);
        // DB 행만 지우면 S3 객체가 그대로 남습니다.
        imageCleanup.deleteAfterCommit(item.getImageUrl());
    }

    /** 다른 장식장의 굿즈 id 를 넣어도 건드려지지 않도록 확인합니다. */
    private ExhibitionItem getItemOf(Long exhibitionId, Long itemId) {
        ExhibitionItem item = exhibitionItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ExhibitionErrorCode.ITEM_NOT_FOUND));
        if (!item.belongsTo(exhibitionId)) {
            throw new BusinessException(ExhibitionErrorCode.ITEM_NOT_FOUND);
        }
        return item;
    }

    /**
     * 업로드된 파일이 정말 이미지인지 확인합니다.
     *
     * <p><b>{@code Content-Type} 헤더만 믿으면 안 됩니다.</b> 클라이언트가 보내는 값이라
     * 아무 파일에나 {@code image/png} 를 붙일 수 있고, 그러면 처리에 실패한 뒤 원본이 그대로
     * 저장되어 <b>공개 URL 로 서빙됩니다</b> — 업로드 창구가 곧 파일 호스팅이 됩니다.
     * 그래서 실제 바이트를 디코더에 물어봅니다.
     */
    private void validateImage(MultipartFile image, byte[] data) {
        if (image == null || image.isEmpty() || data.length == 0) {
            throw new BusinessException(ExhibitionErrorCode.EMPTY_IMAGE);
        }
        String contentType = image.getContentType();
        if (contentType == null || !SUPPORTED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ExhibitionErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        if (!ImageInspector.isSupported(data)) {
            throw new BusinessException(ExhibitionErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ExhibitionErrorCode.IMAGE_PROCESSING_FAILED);
        }
    }
}
