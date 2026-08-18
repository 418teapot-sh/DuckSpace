package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.dto.request.CreateExhibitionRequest;
import com.duckspace.domain.exhibition.dto.request.UpdateExhibitionRequest;
import com.duckspace.domain.exhibition.dto.response.ExhibitionDetailResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionSummaryResponse;
import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.domain.exhibition.image.ImageCleanup;
import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import com.duckspace.domain.exhibition.repository.ExhibitionLikeRepository;
import com.duckspace.domain.exhibition.repository.ExhibitionRepository;
import com.duckspace.global.exception.BusinessException;
import com.duckspace.global.support.Paging;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** 내 장식장은 한 화면에 다 보이는 편이 자연스러워서 기본값을 크게 잡습니다. */
    private static final int MINE_DEFAULT_LIMIT = 20;

    private final ExhibitionRepository exhibitionRepository;
    private final ExhibitionItemRepository exhibitionItemRepository;
    private final ExhibitionLikeRepository exhibitionLikeRepository;
    private final ImageCleanup imageCleanup;

    @Transactional
    public ExhibitionDetailResponse create(Long userId, CreateExhibitionRequest request) {
        Exhibition saved = exhibitionRepository.save(
                new Exhibition(userId, request.name(), request.themeCode()));
        return ExhibitionDetailResponse.of(saved, userId, 0, false, List.of());
    }

    public ExhibitionDetailResponse getDetail(Long exhibitionId, Long viewerId) {
        Exhibition exhibition = getExhibition(exhibitionId);
        return toDetail(exhibition, viewerId);
    }

    @Transactional
    public ExhibitionDetailResponse rename(Long exhibitionId, Long userId, UpdateExhibitionRequest request) {
        Exhibition exhibition = getOwnedExhibition(exhibitionId, userId);
        exhibition.rename(request.name());
        exhibition.changeTheme(request.themeCode());
        // 이미 들고 있는 엔티티로 응답을 만듭니다. getDetail 을 다시 부르면 findById 부터 반복됩니다.
        return toDetail(exhibition, userId);
    }

    /** 장식장을 지우면 그 안의 굿즈와 좋아요도 함께 사라집니다. */
    @Transactional
    public void delete(Long exhibitionId, Long userId) {
        Exhibition exhibition = getOwnedExhibition(exhibitionId, userId);

        // 행을 지우기 전에 이미지 주소를 챙겨둡니다. 지운 뒤에는 알아낼 방법이 없어서
        // S3 객체가 영구히 남습니다.
        List<String> imageUrls = exhibitionItemRepository.findImageUrlsByExhibitionId(exhibitionId);

        exhibitionItemRepository.deleteByExhibitionId(exhibitionId);
        exhibitionLikeRepository.deleteByExhibitionId(exhibitionId);
        exhibitionRepository.delete(exhibition);

        // 공유 여부(보관함 소유·다른 장식장 사용)는 ImageCleanup 이 삭제 직전에 URL 별로
        // 판단합니다. 커밋 후 시점이라 이 장식장의 행은 이미 사라져, 남은 참조만 잡힙니다.
        imageCleanup.deleteAfterCommit(imageUrls);
    }

    /**
     * 내 장식장 목록. 마이페이지에서 씁니다. 최근에 만든 것부터입니다.
     *
     * <p>{@code likedByMe} 는 <b>내가 내 장식장에 좋아요를 눌렀는지</b>를 뜻합니다.
     * 목록 카드가 인기·검색과 같은 응답을 쓰기 때문에 그대로 채워 보냅니다.
     */
    public List<ExhibitionSummaryResponse> getMine(Long userId, Integer limit) {
        List<Long> ids = exhibitionRepository.findIdsByUserId(
                userId, PageRequest.of(0, Paging.normalize(limit, MINE_DEFAULT_LIMIT, MAX_LIMIT)));
        return toSummaries(ids, userId);
    }

    /** 홈 화면 "인기 전시장". 좋아요가 많은 순입니다. */
    public List<ExhibitionSummaryResponse> getPopular(Integer limit, Long viewerId) {
        List<Long> ids = exhibitionRepository.findPopularIds(
                PageRequest.of(0, Paging.normalize(limit, DEFAULT_LIMIT, MAX_LIMIT)));
        return toSummaries(ids, viewerId);
    }

    /**
     * 전시 검색. 굿즈 이름이 걸리면 그 굿즈가 놓인 장식장을 결과로 돌려줍니다.
     *
     * <p>키워드의 {@code %} · {@code _} 는 와일드카드로 동작하지 않도록 이스케이프합니다.
     * 이걸 빼면 {@code %} 한 글자로 전체 검색이 됩니다.
     */
    public List<ExhibitionSummaryResponse> search(String keyword, Integer limit, Long viewerId) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        List<Long> ids = exhibitionRepository.searchExhibitionIdsByItem(
                escapeLike(keyword.trim()), ItemStatus.READY,
                PageRequest.of(0, Paging.normalize(limit, DEFAULT_LIMIT, MAX_LIMIT)));
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

    private ExhibitionDetailResponse toDetail(Exhibition exhibition, Long viewerId) {
        boolean owner = exhibition.isOwnedBy(viewerId);
        List<ExhibitionItem> items = exhibitionItemRepository
                .findByExhibitionIdAndStatusInOrderByIdAsc(exhibition.getId(), ItemStatus.visibleTo(owner));

        long likeCount = exhibitionLikeRepository.countByExhibitionId(exhibition.getId());
        boolean likedByMe = exhibitionLikeRepository.existsByExhibitionIdAndUserId(exhibition.getId(), viewerId);

        return ExhibitionDetailResponse.of(exhibition, viewerId, likeCount, likedByMe, items);
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

        // imageUrl 이 null 인 굿즈가 섞이면 Collectors.toMap 이 NPE 를 냅니다.
        // 지금은 READY 만 조회해 항상 채워져 있지만, 조건이 바뀌면 조용히 터지는 자리라 걸러둡니다.
        Map<Long, String> thumbnails = exhibitionItemRepository
                .findFirstItemOfEach(orderedIds, ItemStatus.READY).stream()
                .filter(item -> item.getImageUrl() != null)
                .collect(Collectors.toMap(item -> item.getExhibition().getId(), ExhibitionItem::getImageUrl));

        Map<Long, Long> likeCounts = exhibitionLikeRepository.countByExhibitionIds(orderedIds).stream()
                .collect(Collectors.toMap(
                        ExhibitionLikeRepository.LikeCount::getExhibitionId,
                        ExhibitionLikeRepository.LikeCount::getLikeCount));

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
}
