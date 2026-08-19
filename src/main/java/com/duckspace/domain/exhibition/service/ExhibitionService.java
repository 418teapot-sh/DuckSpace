package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.dto.request.CreateExhibitionRequest;
import com.duckspace.domain.exhibition.dto.request.UpdateExhibitionRequest;
import com.duckspace.domain.exhibition.dto.response.ExhibitionDetailResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionSummaryPageResponse;
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
import org.springframework.data.domain.Pageable;
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

    /**
     * 카드 미리보기에 담을 굿즈 개수 상한(장식장 하나당). 카드 썸네일 크기에서 그 이상은
     * 어차피 안 보이는데, 목록(최대 {@value #MAX_LIMIT}개 장식장)이 인증 없이 열려 있어서
     * 상한이 없으면 응답이 장식장 수 × 굿즈 수만큼 무한정 커집니다(#77 리뷰).
     */
    private static final int MAX_PREVIEW_ITEMS = 20;

    /** 회원가입 시 자동으로 만들어주는 기본 장식장의 이름. */
    private static final String DEFAULT_NAME = "내 장식장";

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

    /** 회원가입 시 기본으로 만들어주는 장식장. 이름은 나중에 사용자가 바꿀 수 있습니다. */
    @Transactional
    public void createDefault(Long userId) {
        exhibitionRepository.save(new Exhibition(userId, DEFAULT_NAME, Exhibition.DEFAULT_THEME_CODE));
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
     * 내 장식장 목록. 마이페이지에서 씁니다. 만든 순서대로(오래된 것부터)입니다.
     *
     * <p>{@code likedByMe} 는 <b>내가 내 장식장에 좋아요를 눌렀는지</b>를 뜻합니다.
     * 목록 카드가 인기·검색과 같은 응답을 쓰기 때문에 그대로 채워 보냅니다.
     */
    public List<ExhibitionSummaryResponse> getMine(Long userId, Long cursor, Integer limit) {
        Pageable pageable = PageRequest.of(0, Paging.normalize(limit, MINE_DEFAULT_LIMIT, MAX_LIMIT));

        // 커서가 없으면 예전과 완전히 같습니다. 응답 모양도 그대로라 프론트는 안 고쳐도 됩니다 —
        // 다음 장을 원할 때만 마지막 항목의 exhibitionId 를 cursor 로 넣으면 됩니다.
        List<Long> ids = (cursor == null)
                ? exhibitionRepository.findIdsByUserId(userId, pageable)
                : exhibitionRepository.findIdsByUserIdAfter(userId, cursor, pageable);

        return toSummaries(ids, userId);
    }

    /**
     * 특정 유저의 장식장 목록. 다른 유저 프로필의 장식장 탭에서 씁니다.
     *
     * <p>정렬·기본값·상한은 {@link #getMine} 과 같습니다(만든 순서대로, 오래된 것부터).
     * {@code getMine} 과 달리 조회 대상({@code userId})과 보는 사람({@code viewerId})이
     * 분리돼 있습니다 — 남의 장식장 목록을 보는 것이라 {@code likedByMe} 는 실제 보는 사람
     * 기준입니다. 장식장이 하나도 없으면 빈 목록입니다({@link #getPrimary} 와 달리 404 아님).
     */
    public List<ExhibitionSummaryResponse> getByUser(Long userId, Long viewerId, Integer limit) {
        List<Long> ids = exhibitionRepository.findIdsByUserId(
                userId, PageRequest.of(0, Paging.normalize(limit, MINE_DEFAULT_LIMIT, MAX_LIMIT)));
        return toSummaries(ids, viewerId);
    }

    /**
     * 유저의 대표 장식장. 프로필 화면에서 "이 사람의 장식장" 버튼으로 이동할 때 씁니다.
     *
     * <p>유저가 장식장을 여러 개 가질 수 있어 "대표"가 모호하지만, 가장 먼저 만든(id가 가장
     * 작은) 것을 대표로 취급하기로 했습니다(#65). 지금은 가입 시 자동 생성되는 기본 장식장
     * 하나뿐이라 사실상 항상 그 하나가 나옵니다.
     */
    public ExhibitionSummaryResponse getPrimary(Long userId, Long viewerId) {
        List<Long> ids = exhibitionRepository.findIdsByUserId(userId, PageRequest.of(0, 1));
        if (ids.isEmpty()) {
            throw new BusinessException(ExhibitionErrorCode.EXHIBITION_NOT_FOUND);
        }
        return toSummaries(ids, viewerId).get(0);
    }

    /** 홈 화면 "인기 전시장". 좋아요가 많은 순입니다. */
    public List<ExhibitionSummaryResponse> getPopular(Integer limit, Long viewerId) {
        List<Long> ids = exhibitionRepository.findPopularIds(
                PageRequest.of(0, Paging.normalize(limit, DEFAULT_LIMIT, MAX_LIMIT)));
        return toSummaries(ids, viewerId);
    }

    /**
     * 검색 탭 기본 화면의 장식장 피드. 필터 없이 최신 등록순 커서 페이징입니다.
     *
     * <p>{@code cursor} 가 0 이하면 첫 페이지로 취급합니다 — 장식장 id 는 1부터 시작해서,
     * 0 이하를 그대로 리포지토리에 넘기면 데이터가 있어도 빈 목록이 나옵니다. 정상 흐름
     * (응답의 {@code nextCursor} 를 그대로 다음 요청에 넣는 방식)에서는 안 생기지만,
     * 프론트가 커서를 {@code null} 대신 {@code 0}으로 초기화하는 실수를 해도 "더 이상 데이터
     * 없음"과 구분 안 되는 상태로 조용히 새어나가지 않도록 여기서 방어합니다(PR #86 리뷰).
     */
    public ExhibitionSummaryPageResponse getRecent(Long cursor, Integer size, Long viewerId) {
        int pageSize = Paging.normalize(size, DEFAULT_LIMIT, MAX_LIMIT);
        Pageable pageable = PageRequest.of(0, pageSize + 1);
        Long normalizedCursor = (cursor == null || cursor <= 0) ? null : cursor;

        List<Long> found = exhibitionRepository.findRecentIds(normalizedCursor, pageable);

        Paging.Slice<Long> sliced = Paging.slice(found, pageSize, id -> id);
        return new ExhibitionSummaryPageResponse(
                toSummaries(sliced.page(), viewerId), sliced.nextCursor(), sliced.hasNext());
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
     * <p>장식장마다 굿즈 전체·좋아요 수·내 좋아요 여부를 따로 조회하면 N+1 이 되므로
     * <b>각각 쿼리 한 번</b>으로 모아서 가져옵니다. (엔티티 조회까지 총 4쿼리)
     *
     * <p>{@code items} 는 카드 미리보기에 배경 위 굿즈 배치를 그대로 그리기 위한 것입니다(#75).
     * 대표 이미지({@code thumbnailUrl})는 예전엔 {@code findFirstItemOfEach}(min(id) 굿즈)로
     * 따로 조회했지만, {@code items} 도 같은 조건(READY, id asc)이라 결과가 항상 같은 값이라서
     * {@code items} 의 첫 번째에서 뽑는 걸로 바꿔 쿼리 하나를 줄였습니다(#77 리뷰).
     *
     * <p>{@code items} 는 항상 READY 굿즈만입니다 — 소유자여도 PENDING·FAILED 는 안 들어갑니다.
     * 상세 화면({@code getDetail})은 소유자에게 처리 중인 굿즈까지 보여주지만(고쳐야 할 게
     * 있으니까), 카드는 그릴 이미지가 없는 굿즈를 미리보기에 넣을 이유가 없어서 의도적으로
     * 다릅니다(#77 리뷰에서 확인).
     *
     * <p>굿즈 개수는 장식장당 {@value #MAX_PREVIEW_ITEMS}개로 자릅니다 — 목록 자체가 최대
     * {@value #MAX_LIMIT}개 장식장을 담는 데다, 이 경로 상당수가 인증 없이 열려 있어서 상한이
     * 없으면 응답이 무한정 커집니다.
     */
    private List<ExhibitionSummaryResponse> toSummaries(List<Long> orderedIds, Long viewerId) {
        if (orderedIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Exhibition> exhibitions = exhibitionRepository.findAllById(orderedIds).stream()
                .collect(Collectors.toMap(Exhibition::getId, Function.identity()));

        // id asc 순으로 이미 정렬돼서 나오므로(리포지토리 쿼리), groupingBy 결과 리스트도 그 순서를 유지합니다.
        Map<Long, List<ExhibitionItem>> itemsByExhibition = exhibitionItemRepository
                .findAllByExhibitionIdsAndStatus(orderedIds, ItemStatus.READY).stream()
                .collect(Collectors.groupingBy(item -> item.getExhibition().getId()));

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
                .map(exhibition -> {
                    List<ExhibitionItem> items = itemsByExhibition
                            .getOrDefault(exhibition.getId(), List.of()).stream()
                            .limit(MAX_PREVIEW_ITEMS)
                            .toList();
                    String thumbnailUrl = items.isEmpty() ? null : items.get(0).getImageUrl();
                    return ExhibitionSummaryResponse.of(
                            exhibition,
                            thumbnailUrl,
                            items,
                            likeCounts.getOrDefault(exhibition.getId(), 0L),
                            likedByMe.contains(exhibition.getId()));
                })
                .toList();
    }

    private static String escapeLike(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
