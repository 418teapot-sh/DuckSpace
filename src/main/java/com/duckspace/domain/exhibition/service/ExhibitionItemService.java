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
import com.duckspace.domain.exhibition.image.MultipartImageValidator;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExhibitionItemService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

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
        byte[] data = MultipartImageValidator.readBytes(image);
        MultipartImageValidator.validate(image, data);

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
        ExhibitionItem item = exhibitionItemRepository.findOwnedForUpdate(itemId, exhibitionId)
                .orElseThrow(() -> new BusinessException(ExhibitionErrorCode.ITEM_NOT_FOUND));

        if (item.getStatus() != ItemStatus.FAILED
                && !AbandonedPending.isAbandoned(item.getStatus(), item.getUpdatedAt())) {
            // 뒤에 온 요청은 앞 요청이 PENDING 으로 바꿔둔 것을 보고 여기서 물러납니다.
            throw new BusinessException(ExhibitionErrorCode.ITEM_NOT_RETRYABLE);
        }
        String source = item.getImageUrl();
        if (source == null || source.isBlank()) {
            throw new BusinessException(ExhibitionErrorCode.RETRY_SOURCE_MISSING);
        }

        item.markPending();
        // 이미 PENDING(방치)이었다면 markPending 이 no-op 이라 updatedAt 이 그대로 남고,
        // 그러면 계속 "방치됨" 으로 보여 연타마다 재처리가 중복 접수됩니다. 시계를 되감습니다.
        exhibitionItemRepository.touchUpdatedAt(item.getId(), LocalDateTime.now());
        eventPublisher.publishEvent(
                new ItemImageRetryRequestedEvent(item.getId(), exhibitionId, source));

        return ExhibitionItemResponse.from(item);
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

        Paging.Slice<ExhibitionItem> sliced = Paging.slice(found, pageSize, ExhibitionItem::getId);
        return new ExhibitionItemPageResponse(
                sliced.page().stream().map(ExhibitionItemResponse::from).toList(),
                sliced.nextCursor(), sliced.hasNext());
    }

    @Transactional
    public void delete(Long exhibitionId, Long itemId, Long userId) {
        exhibitionService.getOwnedExhibition(exhibitionId, userId);
        ExhibitionItem item = getItemOf(exhibitionId, itemId);

        exhibitionItemRepository.delete(item);
        // 공유 여부(보관함 소유·다른 굿즈 재사용)는 ImageCleanup 이 삭제 직전에 판단합니다.
        // 여기서 미리 판단하면 판단과 삭제 사이에 새 배치가 끼어드는 경합이 남습니다.
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

}
