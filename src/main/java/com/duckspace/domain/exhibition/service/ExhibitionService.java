package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.dto.request.CreateExhibitionRequest;
import com.duckspace.domain.exhibition.dto.request.UpdateExhibitionRequest;
import com.duckspace.domain.exhibition.dto.response.ExhibitionDetailResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionSummaryResponse;
import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import com.duckspace.domain.exhibition.repository.ExhibitionLikeRepository;
import com.duckspace.domain.exhibition.repository.ExhibitionRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExhibitionService {

    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 10;

    private final ExhibitionRepository exhibitionRepository;
    private final ExhibitionItemRepository exhibitionItemRepository;
    private final ExhibitionLikeRepository exhibitionLikeRepository;

    @Transactional
    public ExhibitionDetailResponse create(Long userId, CreateExhibitionRequest request) {
        Exhibition saved = exhibitionRepository.save(new Exhibition(userId, request.name()));
        return ExhibitionDetailResponse.of(saved, userId, 0, false, List.of());
    }

    public ExhibitionDetailResponse getDetail(Long exhibitionId, Long viewerId) {
        Exhibition exhibition = getExhibition(exhibitionId);
        List<ExhibitionItem> items = exhibitionItemRepository.findByExhibitionIdOrderByIdAsc(exhibitionId);

        long likeCount = exhibitionLikeRepository.countByExhibitionIds(List.of(exhibitionId)).stream()
                .findFirst()
                .map(ExhibitionLikeRepository.LikeCount::getLikeCount)
                .orElse(0L);
        boolean likedByMe = exhibitionLikeRepository.existsByExhibitionIdAndUserId(exhibitionId, viewerId);

        return ExhibitionDetailResponse.of(exhibition, viewerId, likeCount, likedByMe, items);
    }

    @Transactional
    public ExhibitionDetailResponse rename(Long exhibitionId, Long userId, UpdateExhibitionRequest request) {
        Exhibition exhibition = getOwnedExhibition(exhibitionId, userId);
        exhibition.rename(request.name());
        return getDetail(exhibitionId, userId);
    }

    /** 장식장을 지우면 그 안의 굿즈와 좋아요도 함께 사라집니다. */
    @Transactional
    public void delete(Long exhibitionId, Long userId) {
        Exhibition exhibition = getOwnedExhibition(exhibitionId, userId);

        exhibitionItemRepository.deleteByExhibitionId(exhibitionId);
        exhibitionLikeRepository.deleteByExhibitionId(exhibitionId);
        exhibitionRepository.delete(exhibition);
    }

    /** 홈 화면 "인기 전시장". 좋아요가 많은 순입니다. */
    public List<ExhibitionSummaryResponse> getPopular(Integer limit, Long viewerId) {
        List<Long> ids = exhibitionRepository.findPopularIds(PageRequest.of(0, normalizeLimit(limit)));
        return toSummaries(ids, viewerId);
    }

    /**
     * 전시 검색. 굿즈 이름·브랜드가 걸리면 그 굿즈가 놓인 장식장을 결과로 돌려줍니다.
     *
     * <p>키워드의 {@code %} · {@code _} 는 와일드카드로 동작하지 않도록 이스케이프합니다.
     * 이걸 빼면 {@code %} 한 글자로 전체 검색이 됩니다.
     */
    public List<ExhibitionSummaryResponse> search(String keyword, Integer limit, Long viewerId) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        List<Long> ids = exhibitionRepository.searchExhibitionIdsByItem(
                escapeLike(keyword.trim()), ItemStatus.READY, PageRequest.of(0, normalizeLimit(limit)));
        return toSummaries(ids, viewerId);
    }

    public Exhibition getExhibition(Long exhibitionId) {
        return exhibitionRepository.findById(exhibitionId)
                .orElseThrow(() -> new BusinessException(ExhibitionErrorCode.EXHIBITION_NOT_FOUND));
    }

    public Exhibition getOwnedExhibition(Long exhibitionId, Long userId) {
        Exhibition exhibition = getExhibition(exhibitionId);
        if (!exhibition.isOwnedBy(userId)) {
            throw new BusinessException(ExhibitionErrorCode.NOT_EXHIBITION_OWNER);
        }
        return exhibition;
    }

    /**
     * id 목록을 카드 응답으로 채웁니다.
     *
     * <p>장식장마다 대표 이미지·좋아요 수·내 좋아요 여부를 따로 조회하면 N+1 이 되므로
     * <b>각각 쿼리 한 번</b>으로 모아서 가져옵니다. (엔티티 조회까지 총 4쿼리)
     */
    private List<ExhibitionSummaryResponse> toSummaries(List<Long> orderedIds, Long viewerId) {
        if (orderedIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Exhibition> exhibitions = exhibitionRepository.findAllById(orderedIds).stream()
                .collect(Collectors.toMap(Exhibition::getId, Function.identity()));

        Map<Long, String> thumbnails = exhibitionItemRepository
                .findFirstItemOfEach(orderedIds, ItemStatus.READY).stream()
                .collect(Collectors.toMap(item -> item.getExhibition().getId(), ExhibitionItem::getImageUrl));

        Map<Long, Long> likeCounts = new HashMap<>();
        for (ExhibitionLikeRepository.LikeCount row : exhibitionLikeRepository.countByExhibitionIds(orderedIds)) {
            likeCounts.put(row.getExhibitionId(), row.getLikeCount());
        }

        Set<Long> likedByMe = new HashSet<>(
                exhibitionLikeRepository.findLikedExhibitionIds(viewerId, orderedIds));

        // findAllById 는 순서를 보장하지 않으므로, 정렬해서 받은 id 순서를 그대로 복원합니다.
        return orderedIds.stream()
                .map(exhibitions::get)
                .filter(Objects::nonNull)
                .map(exhibition -> ExhibitionSummaryResponse.of(
                        exhibition,
                        thumbnails.get(exhibition.getId()),
                        likeCounts.getOrDefault(exhibition.getId(), 0L),
                        likedByMe.contains(exhibition.getId())))
                .toList();
    }

    private static String escapeLike(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
