package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.dto.request.AddItemRequest;
import com.duckspace.domain.exhibition.dto.response.ExhibitionItemPageResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionItemResponse;
import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExhibitionItemService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ExhibitionItemRepository exhibitionItemRepository;
    private final ExhibitionService exhibitionService;

    /**
     * 슬롯에 굿즈를 배치합니다. 이미 굿즈가 놓인 슬롯이면 거부합니다.
     *
     * <p>이미지 파이프라인이 붙기 전이라 상태는 곧바로 {@link ItemStatus#READY} 입니다.
     * 파일 업로드 경로가 생기면 그쪽은 {@code PENDING} 으로 시작합니다.
     */
    @Transactional
    public ExhibitionItemResponse add(Long exhibitionId, Long userId, AddItemRequest request) {
        Exhibition exhibition = exhibitionService.getOwnedExhibition(exhibitionId, userId);

        if (exhibitionItemRepository.existsByExhibitionIdAndSlotId(exhibitionId, request.slotId())) {
            throw new BusinessException(ExhibitionErrorCode.SLOT_ALREADY_OCCUPIED);
        }

        ExhibitionItem item = new ExhibitionItem(
                exhibition, request.slotId(), request.imageUrl(),
                request.itemName(), request.brand(), request.description(), ItemStatus.READY);

        try {
            return ExhibitionItemResponse.from(exhibitionItemRepository.saveAndFlush(item));
        } catch (DataIntegrityViolationException e) {
            // 같은 슬롯에 동시에 배치를 시도한 경우. 사전 확인만으로는 막지 못합니다.
            throw new BusinessException(ExhibitionErrorCode.SLOT_ALREADY_OCCUPIED);
        }
    }

    /** 전시된 굿즈 그리드. 최신순 커서 페이징입니다. */
    public ExhibitionItemPageResponse list(Long exhibitionId, Long cursor, Integer size) {
        exhibitionService.getExhibition(exhibitionId);

        int pageSize = normalizeSize(size);
        // 다음 페이지 존재 여부를 알기 위해 한 개 더 가져옵니다.
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<ExhibitionItem> found = (cursor == null)
                ? exhibitionItemRepository.findByExhibitionIdOrderByIdDesc(exhibitionId, pageable)
                : exhibitionItemRepository.findByExhibitionIdAndIdLessThanOrderByIdDesc(exhibitionId, cursor, pageable);

        boolean hasNext = found.size() > pageSize;
        List<ExhibitionItem> page = hasNext ? found.subList(0, pageSize) : found;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        return new ExhibitionItemPageResponse(
                page.stream().map(ExhibitionItemResponse::from).toList(), nextCursor, hasNext);
    }

    @Transactional
    public void delete(Long exhibitionId, Long itemId, Long userId) {
        exhibitionService.getOwnedExhibition(exhibitionId, userId);

        ExhibitionItem item = exhibitionItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ExhibitionErrorCode.ITEM_NOT_FOUND));

        // 다른 장식장의 아이템 id 를 넣어도 지워지지 않도록 확인합니다.
        if (!item.belongsTo(exhibitionId)) {
            throw new BusinessException(ExhibitionErrorCode.ITEM_NOT_FOUND);
        }

        exhibitionItemRepository.delete(item);
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
