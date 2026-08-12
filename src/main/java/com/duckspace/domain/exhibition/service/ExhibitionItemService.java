package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.dto.request.AddItemRequest;
import com.duckspace.domain.exhibition.dto.request.UpdatePositionRequest;
import com.duckspace.domain.exhibition.dto.response.ExhibitionItemPageResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionItemResponse;
import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import com.duckspace.global.exception.BusinessException;
import com.duckspace.global.support.Paging;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 장식장에 굿즈를 배치합니다.
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
        exhibitionItemRepository.delete(getItemOf(exhibitionId, itemId));
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
