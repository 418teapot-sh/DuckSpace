package com.duckspace.domain.post.service;

import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.post.dto.request.CasualPostRequest;
import com.duckspace.domain.post.dto.request.ExchangePostRequest;
import com.duckspace.domain.post.dto.request.OfferedItemRequest;
import com.duckspace.domain.post.dto.request.WantedItemRequest;
import com.duckspace.domain.post.dto.response.CasualPostSummaryResponse;
import com.duckspace.domain.post.dto.response.ExchangePostSummaryResponse;
import com.duckspace.domain.post.dto.response.PostDetailResponse;
import com.duckspace.domain.post.entity.BoardType;
import com.duckspace.domain.post.entity.ExchangeDetail;
import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.entity.PostHashtag;
import com.duckspace.domain.post.entity.PostImage;
import com.duckspace.domain.post.entity.TradeItem;
import com.duckspace.domain.post.entity.TradeItemSide;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.post.repository.CommentRepository;
import com.duckspace.domain.post.repository.ExchangeApplicationRepository;
import com.duckspace.domain.post.repository.ExchangeDetailRepository;
import com.duckspace.domain.post.repository.PendingPostImageRepository;
import com.duckspace.domain.post.repository.PostHashtagRepository;
import com.duckspace.domain.post.repository.PostIdCount;
import com.duckspace.domain.post.repository.PostThumbnail;
import com.duckspace.domain.post.repository.PostImageRepository;
import com.duckspace.domain.post.repository.PostLikeRepository;
import com.duckspace.domain.post.repository.PostRepository;
import com.duckspace.domain.post.repository.TradeItemRepository;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.exception.BusinessException;
import com.duckspace.global.support.LikeEscaper;
import com.duckspace.global.support.Paging;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final ExchangeDetailRepository exchangeDetailRepository;
    private final ExchangeApplicationRepository exchangeApplicationRepository;
    private final TradeItemRepository tradeItemRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ExchangeApplicationWriter exchangeApplicationWriter;
    private final PendingPostImageRepository pendingPostImageRepository;
    private final ImageStorage imageStorage;

    @Transactional
    public Long createCasual(Long userId, CasualPostRequest request) {
        requireContent(request.content());
        Post post = postRepository.save(Post.createCasual(userId, request.content()));
        saveImages(post, request.imageUrls());
        saveHashtags(post, request.hashtags());
        return post.getId();
    }

    public List<CasualPostSummaryResponse> listCasual(String keyword, Long cursor, Integer size,
                                                      Long authorId, Long viewerId) {
        List<Post> posts = postRepository.search(
                BoardType.CASUAL, cursor, normalizeKeyword(keyword), authorId, pageable(size));
        if (posts.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, Long> likeCounts = batchLikeCounts(postIds);
        Map<Long, Long> commentCounts = batchCommentCounts(postIds);
        Map<Long, User> authors = batchAuthors(posts);
        Map<Long, String> thumbnails = batchThumbnails(postIds);
        Set<Long> likedPostIds = batchLikedPostIds(viewerId, postIds);

        return posts.stream()
                .map(post -> {
                    User author = authors.get(post.getUserId());
                    return new CasualPostSummaryResponse(
                            post.getId(),
                            post.getContent(),
                            post.getUserId(),
                            author == null ? null : author.getNickname(),
                            author == null ? null : author.getProfileImageUrl(),
                            post.getCreatedAt(),
                            likeCounts.getOrDefault(post.getId(), 0L),
                            commentCounts.getOrDefault(post.getId(), 0L),
                            likedPostIds.contains(post.getId()),
                            thumbnails.get(post.getId()));
                })
                .toList();
    }

    @Transactional
    public Long createExchange(Long userId, ExchangePostRequest request) {
        Post post = postRepository.save(Post.createExchange(userId, request.title(), request.content()));
        ExchangeDetail detail = exchangeDetailRepository.save(
                new ExchangeDetail(post, request.extraCondition(),
                        request.preferredPopupName(), request.preferredDate(), request.preferredTime()));

        OfferedItemRequest offered = request.offeredItem();
        tradeItemRepository.save(TradeItem.offered(
                detail, offered.imageUrl(), offered.itemName(), offered.brand(), offered.condition()));

        WantedItemRequest wanted = request.wantedItem();
        tradeItemRepository.save(TradeItem.wanted(detail, wanted.imageUrl(), wanted.itemName(), wanted.brand()));

        claimImages(Arrays.asList(offered.imageUrl(), wanted.imageUrl()));
        return post.getId();
    }

    public List<ExchangePostSummaryResponse> listExchange(String keyword, Long cursor, Integer size, Long authorId) {
        List<Post> posts = postRepository.search(
                BoardType.EXCHANGE, cursor, normalizeKeyword(keyword), authorId, pageable(size));
        if (posts.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, Long> likeCounts = batchLikeCounts(postIds);
        Map<Long, Long> commentCounts = batchCommentCounts(postIds);
        Map<Long, String> nicknames = batchNicknames(posts);
        Map<Long, ExchangeDetail> details = exchangeDetailRepository.findAllById(postIds).stream()
                .collect(Collectors.toMap(ExchangeDetail::getPostId, Function.identity()));
        Map<Long, List<TradeItem>> itemsByPost = tradeItemRepository.findByExchangeDetail_PostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(item -> item.getExchangeDetail().getPostId()));

        // ExchangeDetail은 교환 글 생성 시 Post와 한 트랜잭션에서 항상 같이 만들어지므로 정상적으로는 없을 수 없지만,
        // 데이터 정합성이 깨진 행 하나 때문에 목록 전체가 죽는 것보다는 그 행만 건너뛰는 쪽이 안전합니다.
        return posts.stream()
                .filter(post -> details.containsKey(post.getId()))
                .map(post -> {
                    ExchangeDetail detail = details.get(post.getId());
                    List<TradeItem> items = itemsByPost.getOrDefault(post.getId(), List.of());
                    return new ExchangePostSummaryResponse(
                            post.getId(),
                            post.getTitle(),
                            detail.getStatus(),
                            itemNameOf(items, TradeItemSide.OFFERED),
                            itemNameOf(items, TradeItemSide.WANTED),
                            post.getUserId(),
                            nicknames.get(post.getUserId()),
                            post.getCreatedAt(),
                            likeCounts.getOrDefault(post.getId(), 0L),
                            commentCounts.getOrDefault(post.getId(), 0L));
                })
                .toList();
    }

    public PostDetailResponse getDetail(Long postId, Long viewerId) {
        Post post = getPost(postId);
        String nickname = userRepository.findById(post.getUserId()).map(User::getNickname).orElse(null);
        long likeCount = postLikeRepository.countByPostId(postId);
        long commentCount = commentRepository.countByPost_Id(postId);
        boolean liked = postLikeRepository.existsByPostIdAndUserId(postId, viewerId);
        boolean mine = post.isOwnedBy(viewerId);

        List<String> imageUrls = List.of();
        List<String> hashtags = List.of();
        PostDetailResponse.ExchangeInfo exchangeInfo = null;

        if (post.getBoardType() == BoardType.CASUAL) {
            imageUrls = postImageRepository.findByPost_IdOrderBySortOrderAsc(postId).stream()
                    .map(PostImage::getImageUrl)
                    .toList();
            hashtags = postHashtagRepository.findByPost_Id(postId).stream()
                    .map(PostHashtag::getTag)
                    .toList();
        } else {
            ExchangeDetail detail = exchangeDetailRepository.findById(postId)
                    .orElseThrow(() -> new BusinessException(PostErrorCode.POST_NOT_FOUND));
            List<TradeItem> items = tradeItemRepository.findByExchangeDetail_PostIdOrderBySideAsc(postId);
            exchangeInfo = new PostDetailResponse.ExchangeInfo(
                    detail.getStatus(),
                    detail.getExtraCondition(),
                    detail.getPreferredPopupName(),
                    detail.getPreferredDate(),
                    detail.getPreferredTime(),
                    toItemInfo(findBySide(items, TradeItemSide.OFFERED)),
                    toItemInfo(findBySide(items, TradeItemSide.WANTED)));
        }

        return new PostDetailResponse(
                post.getId(), post.getBoardType(), post.getTitle(), post.getContent(),
                post.getUserId(), nickname, post.getCreatedAt(), mine,
                likeCount, liked, commentCount, imageUrls, hashtags, exchangeInfo);
    }

    /** PATCH는 부분 수정입니다. content/imageUrls/hashtags를 아예 안 보내면(null) 기존 값을 그대로 둡니다. */
    @Transactional
    public void updateCasual(Long postId, Long userId, CasualPostRequest request) {
        Post post = getOwnedPost(postId, userId);
        requireBoardType(post, BoardType.CASUAL);

        if (request.content() != null) {
            requireContent(request.content());
            post.updateContent(request.content());
        }

        if (request.imageUrls() != null) {
            List<String> oldUrls = postImageRepository.findByPost_IdOrderBySortOrderAsc(postId).stream()
                    .map(PostImage::getImageUrl)
                    .toList();
            postImageRepository.deleteByPost_Id(postId);
            saveImages(post, request.imageUrls());

            List<String> removed = oldUrls.stream()
                    .filter(url -> !request.imageUrls().contains(url))
                    .toList();
            deleteImagesAfterCommit(ownedByAuthor(userId, removed));
        }
        if (request.hashtags() != null) {
            postHashtagRepository.deleteByPost_Id(postId);
            saveHashtags(post, request.hashtags());
        }
    }

    /**
     * 소프트 삭제입니다. 댓글/좋아요/사진 등 연관 데이터는 지우지 않고 그대로 둡니다 —
     * 게시글 자체가 조회 경로에서 사라지므로(getPost가 deletedAt is null만 찾음) 어차피 도달할 수 없고,
     * 신고 이력 등을 남겨야 할 수 있어 실제로 지우는 것보다 안전합니다.
     */
    @Transactional
    public void delete(Long postId, Long userId) {
        Post post = getOwnedPost(postId, userId);
        post.delete();
    }

    /**
     * ExchangeApplication이 생기기 전부터 있던 완료 처리 경로입니다. 진행중인 신청이 있다면
     * {@link ExchangeApplicationService#complete}를 거치는 쪽이 정석이지만, 이 경로로 들어와도
     * ACCEPTED 상태 신청이 있으면 같이 COMPLETED로 맞춰서 신청 쪽이 영원히 ACCEPTED로 남는 걸 막습니다.
     */
    @Transactional
    public void completeExchange(Long postId, Long userId) {
        Post post = getOwnedPost(postId, userId);
        requireBoardType(post, BoardType.EXCHANGE);

        ExchangeDetail detail = exchangeDetailRepository.findByPostIdForUpdate(postId)
                .orElseThrow(() -> new BusinessException(PostErrorCode.POST_NOT_FOUND));
        if (detail.isCompleted()) {
            throw new BusinessException(PostErrorCode.EXCHANGE_ALREADY_COMPLETED);
        }
        detail.complete();

        exchangeApplicationWriter.findAcceptedByPostId(postId)
                .ifPresent(application -> {
                    application.complete();
                    exchangeApplicationRepository.save(application);
                });
    }

    Post getPost(Long postId) {
        return postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(PostErrorCode.POST_NOT_FOUND));
    }

    /** package-private: ExchangeApplicationService도 "글쓴이만" 체크에 재사용합니다. */
    Post getOwnedPost(Long postId, Long userId) {
        Post post = getPost(postId);
        if (!post.isOwnedBy(userId)) {
            throw new BusinessException(PostErrorCode.NOT_POST_OWNER);
        }
        return post;
    }

    private void requireBoardType(Post post, BoardType expected) {
        if (post.getBoardType() != expected) {
            throw new BusinessException(PostErrorCode.INVALID_BOARD_TYPE);
        }
    }

    private void requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(PostErrorCode.CONTENT_REQUIRED);
        }
    }

    private void saveImages(Post post, List<String> imageUrls) {
        if (imageUrls == null) {
            return;
        }
        int order = 0;
        for (String imageUrl : imageUrls) {
            postImageRepository.save(new PostImage(post, imageUrl, order++));
        }
        claimImages(imageUrls);
    }

    /**
     * PostImageService.upload가 남겨둔 "아직 안 쓰였다" 표시를 지웁니다. 실제 글 작성에 쓰인
     * URL만 넘기세요 — null/blank는 걸러서 무시합니다(교환 글은 imageUrl이 선택이라 흔합니다).
     */
    private void claimImages(List<String> imageUrls) {
        List<String> used = imageUrls.stream().filter(url -> url != null && !url.isBlank()).toList();
        if (!used.isEmpty()) {
            pendingPostImageRepository.deleteByImageUrlIn(used);
        }
    }

    /**
     * 잡담 글 수정으로 목록에서 빠진 이미지의 실제 파일을 지웁니다. 이미 claimImages로
     * PendingPostImage 표시가 지워진 뒤라 그쪽 정리 대상에도 안 걸리므로, 여기서 안 지우면
     * 완전한 영구 고아가 됩니다(PostImage 행도 지워졌고 PendingPostImage에도 없음).
     *
     * <p>커밋 이후에 지우는 이유는 exhibition의 ImageCleanup과 같습니다 — 트랜잭션이 롤백되면
     * PostImage 행은 살아있는데 실제 파일만 사라지는 걸 막기 위해서입니다.
     */
    /**
     * 삭제 대상을 <b>이 사용자가 올린 게시글 이미지</b>로 좁힙니다.
     *
     * <p>{@code imageUrls}는 클라이언트가 준 값이 그대로 저장된 것이라, 남의 이미지 주소가
     * 섞여 있을 수 있습니다({@code saveImages}가 소유를 확인하지 않습니다). 그대로 지우면
     * <b>남의 프로필 사진·게시글 사진·굿즈 이미지를 저장소에서 지울 수 있습니다</b> —
     * 남의 URL로 글을 만들고 {@code imageUrls: []}로 PATCH하면 그 파일이 사라집니다.
     *
     * <p>{@link ImageStorage#keyOf}는 "우리 저장소 주소인가"까지만 알려주므로, 키에 소유자가
     * 들어 있는 규칙({@link PostImageService#keyPrefixOf})으로 한 번 더 거릅니다.
     *
     * <p>거른 URL은 그냥 안 지웁니다 — 애초에 우리가 지울 대상이 아니었습니다.
     */
    private List<String> ownedByAuthor(Long userId, List<String> imageUrls) {
        String keyPrefix = PostImageService.keyPrefixOf(userId);
        return imageUrls.stream()
                .filter(url -> {
                    String key = imageStorage.keyOf(url);
                    return key != null && key.startsWith(keyPrefix);
                })
                .toList();
    }

    private void deleteImagesAfterCommit(List<String> imageUrls) {
        if (imageUrls.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            imageUrls.forEach(imageStorage::deleteByUrl);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                imageUrls.forEach(imageStorage::deleteByUrl);
            }
        });
    }

    private void saveHashtags(Post post, List<String> hashtags) {
        if (hashtags == null) {
            return;
        }
        for (String tag : hashtags) {
            postHashtagRepository.save(new PostHashtag(post, tag));
        }
    }

    private TradeItem findBySide(List<TradeItem> items, TradeItemSide side) {
        return items.stream().filter(item -> item.getSide() == side).findFirst().orElse(null);
    }

    private String itemNameOf(List<TradeItem> items, TradeItemSide side) {
        TradeItem item = findBySide(items, side);
        return item == null ? null : item.getItemName();
    }

    private PostDetailResponse.TradeItemInfo toItemInfo(TradeItem item) {
        if (item == null) {
            return null;
        }
        return new PostDetailResponse.TradeItemInfo(
                item.getImageUrl(), item.getItemName(), item.getBrand(), item.getCondition());
    }

    private Map<Long, Long> batchLikeCounts(List<Long> postIds) {
        return postLikeRepository.countByPostIdIn(postIds).stream()
                .collect(Collectors.toMap(PostIdCount::getPostId, PostIdCount::getCount));
    }

    private Map<Long, Long> batchCommentCounts(List<Long> postIds) {
        return commentRepository.countByPostIdIn(postIds).stream()
                .collect(Collectors.toMap(PostIdCount::getPostId, PostIdCount::getCount));
    }

    /**
     * 목록 카드에 쓸 대표 이미지를 한 번에 모읍니다. 사진이 없는 글은 결과에 없어서
     * {@code thumbnails.get(postId)} 가 그대로 null 이 됩니다.
     */
    private Map<Long, String> batchThumbnails(List<Long> postIds) {
        return postImageRepository.findThumbnails(postIds, PostImage.THUMBNAIL_SORT_ORDER).stream()
                .collect(Collectors.toMap(
                        PostThumbnail::getPostId,
                        PostThumbnail::getImageUrl,
                        // 한 글에 sortOrder 0 이 둘일 수 없지만, 그런 데이터가 생겨도
                        // toMap 이 IllegalStateException 으로 목록 전체를 죽이지 않게 둡니다.
                        (first, ignored) -> first));
    }

    /**
     * 목록 카드에 그릴 작성자 정보(닉네임 + 프로필 사진)를 한 번에 모읍니다.
     *
     * <p>{@code findNicknamesByIds} 를 쓰면 닉네임만 나와서 프로필 사진이 빠집니다. 그러면
     * 클라이언트가 <b>카드마다 유저를 다시 조회</b>하게 됩니다 — 실제로 그러고 있었습니다.
     * 어차피 {@code findNicknamesByIds} 도 내부에서 {@code findAllById} 로 엔티티를 통째로
     * 불러오므로, 여기서 직접 부르는 것과 쿼리 수가 같습니다.
     */
    private Map<Long, User> batchAuthors(List<Post> posts) {
        List<Long> authorIds = posts.stream().map(Post::getUserId).distinct().toList();
        return userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /**
     * 보는 사람이 좋아요를 누른 글 id 집합. 글마다 exists 를 부르면 목록 크기만큼 쿼리가 나갑니다.
     *
     * <p>{@code viewerId} 가 null 이면 조회하지 않습니다 — {@code l.userId = null} 은 어떤 행과도
     * 일치하지 않아 결과가 반드시 비기 때문입니다. 지금 이 목록은 인증이 필요해서 사실상
     * 안 일어나지만, 나중에 공개로 열리면 그때 바로 새는 자리입니다.
     */
    private Set<Long> batchLikedPostIds(Long viewerId, List<Long> postIds) {
        if (viewerId == null) {
            return Set.of();
        }
        return new HashSet<>(postLikeRepository.findLikedPostIds(viewerId, postIds));
    }

    private Map<Long, String> batchNicknames(List<Post> posts) {
        List<Long> authorIds = posts.stream().map(Post::getUserId).distinct().toList();
        return userRepository.findNicknamesByIds(authorIds);
    }

    /** LIKE 패턴의 %/_/\\ 는 와일드카드로 해석되므로, 검색어에 그대로 들어오면 이스케이프합니다. */
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return LikeEscaper.escape(keyword);
    }

    private Pageable pageable(Integer size) {
        return PageRequest.of(0, Paging.normalize(size, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE));
    }
}
