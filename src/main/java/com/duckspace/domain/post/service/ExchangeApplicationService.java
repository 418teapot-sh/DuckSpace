package com.duckspace.domain.post.service;

import com.duckspace.domain.post.dto.request.ApplicationFilter;
import com.duckspace.domain.post.dto.request.ExchangeApplicationRequest;
import com.duckspace.domain.post.dto.response.ExchangeApplicationResponse;
import com.duckspace.domain.post.entity.BoardType;
import com.duckspace.domain.post.entity.ExchangeApplication;
import com.duckspace.domain.post.entity.ExchangeApplicationStatus;
import com.duckspace.domain.post.entity.ExchangeDetail;
import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.post.repository.ExchangeApplicationRepository;
import com.duckspace.domain.post.repository.ExchangeDetailRepository;
import com.duckspace.domain.post.repository.PostRepository;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.exception.BusinessException;
import com.duckspace.global.support.Paging;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 교환 신청→수락→완료 매칭. 후기/신뢰도 점수는 이 스코프에 없습니다(프론트 목업).
 * 권한 체크("글쓴이만")는 {@link PostService#getOwnedPost}를 재사용합니다.
 *
 * <p>상태 전이: APPLIED -&gt; ACCEPTED -&gt; COMPLETED(종료) / APPLIED -&gt; REJECTED·CANCELLED(종료).
 * ACCEPTED 상태에서도 실제 만남이 무산될 수 있으므로 글쓴이는 reject(), 신청자는 cancel()로
 * 되돌릴 수 있습니다. 한 게시글당 ACCEPTED/COMPLETED 신청은 최대 하나만 허용합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeApplicationService {

    /** apply()에서 이미 진행 중인 신청이 있는지 확인할 때 씁니다. COMPLETED는 detail.isCompleted()가 먼저 막아서 여기 안 옵니다. */
    private static final EnumSet<ExchangeApplicationStatus> ACTIVE_APPLICATION =
            EnumSet.of(ExchangeApplicationStatus.APPLIED, ExchangeApplicationStatus.ACCEPTED);

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ExchangeApplicationRepository exchangeApplicationRepository;
    private final ExchangeDetailRepository exchangeDetailRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostService postService;
    private final ExchangeApplicationWriter exchangeApplicationWriter;

    @Transactional
    public Long apply(Long userId, Long postId, ExchangeApplicationRequest request) {
        Post post = postService.getPost(postId);
        if (post.getBoardType() != BoardType.EXCHANGE) {
            throw new BusinessException(PostErrorCode.INVALID_BOARD_TYPE);
        }
        if (post.isOwnedBy(userId)) {
            throw new BusinessException(PostErrorCode.SELF_APPLICATION_NOT_ALLOWED);
        }

        ExchangeDetail detail = lockPost(postId);
        if (detail.isCompleted()) {
            throw new BusinessException(PostErrorCode.EXCHANGE_ALREADY_COMPLETED);
        }
        if (exchangeApplicationRepository.existsByPostIdAndApplicantUserIdAndStatusIn(
                postId, userId, ACTIVE_APPLICATION)) {
            throw new BusinessException(PostErrorCode.ALREADY_APPLIED);
        }

        ExchangeApplication application = exchangeApplicationRepository.save(
                new ExchangeApplication(postId, userId, request.offeredItemName(), request.offeredImageUrl(),
                        request.offeredBrand(), request.offeredCondition(), request.message()));
        return application.getId();
    }

    /**
     * 해당 게시글에 달린 신청 목록. 글쓴이만 조회할 수 있습니다. 최신순 커서 페이징입니다.
     * cursor를 비우면 최신 신청부터, 값을 주면 그보다 오래된 신청을 내려줍니다(마지막으로 받은 id를 cursor에 넣으면 됨).
     */
    public List<ExchangeApplicationResponse> listByPost(Long postId, Long userId, Long cursor, Integer size) {
        Post post = postService.getOwnedPost(postId, userId);

        List<ExchangeApplication> applications =
                exchangeApplicationRepository.findByPostId(postId, cursor, pageable(size));
        Map<Long, String> nicknames = batchNicknames(applications);
        return applications.stream()
                .map(application -> toResponse(application, post.getTitle(), nicknames.get(application.getApplicantUserId())))
                .toList();
    }

    /** 내 신청함. filter는 sent(내가 신청한 것)/received(내 글에 들어온 신청)만 허용합니다. cursor/size는 listByPost와 같은 규칙입니다. */
    public List<ExchangeApplicationResponse> listMine(Long userId, String filter, Long cursor, Integer size) {
        Pageable pageable = pageable(size);
        List<ExchangeApplication> applications = switch (ApplicationFilter.from(filter)) {
            case SENT -> exchangeApplicationRepository.findByApplicantUserId(userId, cursor, pageable);
            case RECEIVED -> exchangeApplicationRepository.findReceivedByUserId(userId, cursor, pageable);
        };

        Map<Long, String> postTitles = batchPostTitles(applications);
        Map<Long, String> nicknames = batchNicknames(applications);
        return applications.stream()
                .map(application -> toResponse(application,
                        postTitles.get(application.getPostId()), nicknames.get(application.getApplicantUserId())))
                .toList();
    }

    /**
     * 글쓴이만 가능. 같은 게시글에 이미 ACCEPTED/COMPLETED 신청이 있으면 또 수락할 수 없습니다(한 글당 하나만 진행).
     *
     * <p>reject()/cancel()/complete()도 같은 게시글이면 전부 lockPost()로 직렬화됩니다. lockPost()는
     * 기다렸다 얻은 락일 뿐 그 시점의 최신 상태를 보장하지 않으므로(MySQL REPEATABLE READ 스냅샷),
     * 락을 잡은 뒤 {@link ExchangeApplicationWriter}로 상태를 다시 확인합니다.
     */
    @Transactional
    public void accept(Long applicationId, Long userId) {
        ExchangeApplication application = getOwnedApplication(applicationId, userId);
        requireApplied(application.getStatus());
        lockPost(application.getPostId());

        requireApplied(exchangeApplicationWriter.currentStatus(applicationId));
        if (exchangeApplicationWriter.existsAcceptedOrCompleted(application.getPostId())) {
            throw new BusinessException(PostErrorCode.ANOTHER_APPLICATION_ALREADY_ACCEPTED);
        }
        application.accept();
    }

    /** 글쓴이만 가능. 대기중(APPLIED)인 신청을 거절하거나, 이미 수락한 신청을 무를 때도 씁니다. */
    @Transactional
    public void reject(Long applicationId, Long userId) {
        ExchangeApplication application = getOwnedApplication(applicationId, userId);
        requireReversible(application.getStatus());
        lockPost(application.getPostId());

        requireReversible(exchangeApplicationWriter.currentStatus(applicationId));
        application.reject();
    }

    /** 완료 처리는 글쓴이만 가능합니다(신청자는 못 함). 부모 게시글의 ExchangeDetail도 같이 완료 처리합니다. */
    @Transactional
    public void complete(Long applicationId, Long userId) {
        ExchangeApplication application = getApplication(applicationId);
        Long postId = application.getPostId();
        postService.getOwnedPost(postId, userId);

        ExchangeDetail detail = lockPost(postId);
        if (detail.isCompleted()) {
            throw new BusinessException(PostErrorCode.EXCHANGE_ALREADY_COMPLETED);
        }
        if (exchangeApplicationWriter.currentStatus(applicationId) != ExchangeApplicationStatus.ACCEPTED) {
            throw new BusinessException(PostErrorCode.EXCHANGE_APPLICATION_INVALID_STATUS);
        }

        application.complete();
        detail.complete();
    }

    /** 신청자 본인만 가능. 대기중인 신청 취소는 물론, 수락된 신청을 무르는 것도 이 메서드입니다. */
    @Transactional
    public void cancel(Long applicationId, Long userId) {
        ExchangeApplication application = getApplication(applicationId);
        if (!application.isOwnedByApplicant(userId)) {
            throw new BusinessException(PostErrorCode.NOT_APPLICATION_OWNER);
        }
        requireReversible(application.getStatus());
        lockPost(application.getPostId());

        requireReversible(exchangeApplicationWriter.currentStatus(applicationId));
        application.cancel();
    }

    /**
     * "게시글당 하나만 진행" 불변식을 지키기 위해 잠급니다. apply()/accept()가 같은 게시글에 대해
     * 동시에 들어와도 뒤에 들어온 트랜잭션이 앞선 트랜잭션의 커밋을 기다렸다가 최신 상태로 검사하게 됩니다.
     */
    private ExchangeDetail lockPost(Long postId) {
        return exchangeDetailRepository.findByPostIdForUpdate(postId)
                .orElseThrow(() -> new BusinessException(PostErrorCode.POST_NOT_FOUND));
    }

    private Pageable pageable(Integer size) {
        return PageRequest.of(0, Paging.normalize(size, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE));
    }

    private ExchangeApplication getApplication(Long applicationId) {
        return exchangeApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(PostErrorCode.EXCHANGE_APPLICATION_NOT_FOUND));
    }

    /** 신청을 조회하면서 그 신청이 달린 게시글의 글쓴이가 맞는지까지 확인합니다. accept/reject 공용. */
    private ExchangeApplication getOwnedApplication(Long applicationId, Long userId) {
        ExchangeApplication application = getApplication(applicationId);
        postService.getOwnedPost(application.getPostId(), userId);
        return application;
    }

    private void requireApplied(ExchangeApplicationStatus status) {
        if (status != ExchangeApplicationStatus.APPLIED) {
            throw new BusinessException(PostErrorCode.EXCHANGE_APPLICATION_INVALID_STATUS);
        }
    }

    /** reject()/cancel()이 공유하는 가드. 대기중이거나 수락된 상태에서만 되돌릴 수 있습니다. */
    private void requireReversible(ExchangeApplicationStatus status) {
        if (status != ExchangeApplicationStatus.APPLIED && status != ExchangeApplicationStatus.ACCEPTED) {
            throw new BusinessException(PostErrorCode.EXCHANGE_APPLICATION_INVALID_STATUS);
        }
    }

    private Map<Long, String> batchPostTitles(List<ExchangeApplication> applications) {
        List<Long> postIds = applications.stream().map(ExchangeApplication::getPostId).distinct().toList();
        Map<Long, String> titles = new HashMap<>();
        for (Post post : postRepository.findByIdInAndDeletedAtIsNull(postIds)) {
            titles.put(post.getId(), post.getTitle());
        }
        return titles;
    }

    private Map<Long, String> batchNicknames(List<ExchangeApplication> applications) {
        List<Long> applicantIds = applications.stream().map(ExchangeApplication::getApplicantUserId).distinct().toList();
        return userRepository.findNicknamesByIds(applicantIds);
    }

    private ExchangeApplicationResponse toResponse(ExchangeApplication application, String postTitle, String applicantNickname) {
        return new ExchangeApplicationResponse(
                application.getId(),
                application.getPostId(),
                postTitle,
                application.getApplicantUserId(),
                applicantNickname,
                application.getOfferedItemName(),
                application.getOfferedImageUrl(),
                application.getOfferedBrand(),
                application.getOfferedCondition(),
                application.getMessage(),
                application.getStatus(),
                application.getAppliedAt(),
                application.getRespondedAt(),
                application.getCompletedAt());
    }
}
