package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.dto.request.ExhibitionCreateRequest;
import com.duckspace.domain.exhibition.dto.request.ExhibitionItemRequest;
import com.duckspace.domain.exhibition.dto.request.ExhibitionUpdateRequest;
import com.duckspace.domain.exhibition.dto.response.ExhibitionListResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionSummaryResponse;
import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ExhibitionLike;
import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.domain.exhibition.repository.ExhibitionLikeRepository;
import com.duckspace.domain.exhibition.repository.ExhibitionRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExhibitionService {

    private final ExhibitionRepository exhibitionRepository;
    private final ExhibitionLikeRepository exhibitionLikeRepository;

    public ExhibitionListResponse getExhibitions(Pageable pageable) {
        Page<Exhibition> exhibitions = exhibitionRepository.findAllOrderByPopularity(pageable);
        return ExhibitionListResponse.from(exhibitions);
    }

    public List<ExhibitionSummaryResponse> getPopularExhibitions(int limit) {
        return exhibitionRepository.findAllOrderByPopularity(PageRequest.of(0, limit))
                .getContent()
                .stream()
                .map(ExhibitionSummaryResponse::from)
                .toList();
    }

    @Transactional
    public ExhibitionResponse getExhibition(Long exhibitionId) {
        Exhibition exhibition = getExhibitionWithItemsOrThrow(exhibitionId);
        exhibition.increaseViewCount();
        return ExhibitionResponse.from(exhibition);
    }

    @Transactional
    public void toggleLike(Long exhibitionId, Long userId) {
        Exhibition exhibition = getExhibitionOrThrow(exhibitionId);

        exhibitionLikeRepository.findByExhibition_IdAndUserId(exhibitionId, userId)
                .ifPresentOrElse(
                        like -> {
                            exhibitionLikeRepository.delete(like);
                            exhibition.decreaseLikeCount();
                        },
                        () -> {
                            exhibitionLikeRepository.save(
                                    ExhibitionLike.builder()
                                            .exhibition(exhibition)
                                            .userId(userId)
                                            .build()
                            );
                            exhibition.increaseLikeCount();
                        }
                );
    }

    @Transactional
    public ExhibitionResponse createExhibition(ExhibitionCreateRequest request) {
        Exhibition exhibition = Exhibition.builder()
                .title(request.title())
                .description(request.description())
                .thumbnailUrl(request.thumbnailUrl())
                .build();
        exhibition.replaceItems(toItems(request.items()));

        return ExhibitionResponse.from(exhibitionRepository.save(exhibition));
    }

    @Transactional
    public ExhibitionResponse updateExhibition(Long exhibitionId, ExhibitionUpdateRequest request) {
        Exhibition exhibition = getExhibitionWithItemsOrThrow(exhibitionId);
        exhibition.update(request.title(), request.description(), request.thumbnailUrl());
        exhibition.replaceItems(toItems(request.items()));
        return ExhibitionResponse.from(exhibition);
    }

    @Transactional
    public void deleteExhibition(Long exhibitionId) {
        exhibitionRepository.delete(getExhibitionOrThrow(exhibitionId));
    }

    /** 프론트가 넘긴 순서를 그대로 sortOrder로 채택 — 별도 순번 입력을 요구하지 않음 */
    private List<ExhibitionItem> toItems(List<ExhibitionItemRequest> requests) {
        return IntStream.range(0, requests.size())
                .mapToObj(index -> {
                    ExhibitionItemRequest item = requests.get(index);
                    return ExhibitionItem.builder()
                            .name(item.name())
                            .imageUrl(item.imageUrl())
                            .description(item.description())
                            .sortOrder(index)
                            .build();
                })
                .toList();
    }

    private Exhibition getExhibitionOrThrow(Long exhibitionId) {
        return exhibitionRepository.findById(exhibitionId)
                .orElseThrow(() -> new BusinessException(ExhibitionErrorCode.EXHIBITION_NOT_FOUND));
    }

    private Exhibition getExhibitionWithItemsOrThrow(Long exhibitionId) {
        return exhibitionRepository.findByIdWithItems(exhibitionId)
                .orElseThrow(() -> new BusinessException(ExhibitionErrorCode.EXHIBITION_NOT_FOUND));
    }
}