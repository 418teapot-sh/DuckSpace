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
import lombok.RequiredArgsConstructor;
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

    private static final EnumSet<ExchangeApplicationStatus> IN_PROGRESS_OR_DONE =
            EnumSet.of(ExchangeApplicationStatus.ACCEPTED, ExchangeApplicationStatus.COMPLETED);

    private final ExchangeApplicationRepository exchangeApplicationRepository;
    private final ExchangeDetailRepository exchangeDetailRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostService postService;

    @Transactional
    public Long apply(Long userId, Long postId, ExchangeApplicationRequest request) {
        Post post = postService.getPost(postId);
        if (post.getBoardType() != BoardType.EXCHANGE) {
            throw new BusinessException(PostErrorCode.INVALID_BOARD_TYPE);
        }
        if (post.isOwnedBy(userId)) {
            throw new BusinessException(PostErrorCode.SELF_APPLICATION_NOT_ALLOWED);
        }

        ExchangeDetail detail = exchangeDetailRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(PostErrorCode.POST_NOT_FOUND));
        if (detail.isCompleted()) {
            throw new BusinessException(PostErrorCode.EXCHANGE_ALREADY_COMPLETED);
        }
        if (exchangeApplicationRepository.existsByPostIdAndApplicantUserIdAndStatus(
                postId, userId, ExchangeApplicationStatus.APPLIED)) {
            throw new BusinessException(PostErrorCode.ALREADY_APPLIED);
        }

        ExchangeApplication application = exchangeApplicationRepository.save(
                new ExchangeApplication(postId, userId, request.offeredItemName(), request.offeredImageUrl(),
                        request.offeredBrand(), request.offeredCondition(), request.message()));
        return application.getId();
    }

    /** 해당 게시글에 달린 신청 목록. 글쓴이만 조회할 수 있습니다. */
    public List<ExchangeApplicationResponse> listByPost(Long postId, Long userId) {
        Post post = postService.getOwnedPost(postId, userId);

        List<ExchangeApplication> applications = exchangeApplicationRepository.findByPostIdOrderByAppliedAtDescIdDesc(postId);
        Map<Long, String> nicknames = batchNicknames(applications);
        return applications.stream()
                .map(application -> toResponse(application, post.getTitle(), nicknames.get(application.getApplicantUserId())))
                .toList();
    }

    /** 내 신청함. filter는 sent(내가 신청한 것)/received(내 글에 들어온 신청)만 허용합니다. */
    public List<ExchangeApplicationResponse> listMine(Long userId, String filter) {
        List<ExchangeApplication> applications = switch (ApplicationFilter.from(filter)) {
            case SENT -> exchangeApplicationRepository.findByApplicantUserIdOrderByAppliedAtDescIdDesc(userId);
            case RECEIVED -> exchangeApplicationRepository.findReceivedByUserId(userId);
        };

        Map<Long, String> postTitles = batchPostTitles(applications);
        Map<Long, String> nicknames = batchNicknames(applications);
        return applications.stream()
                .map(application -> toResponse(application,
                        postTitles.get(application.getPostId()), nicknames.get(application.getApplicantUserId())))
                .toList();
    }

    /** 글쓴이만 가능. 같은 게시글에 이미 ACCEPTED/COMPLETED 신청이 있으면 또 수락할 수 없습니다(한 글당 하나만 진행). */
    @Transactional
    public void accept(Long applicationId, Long userId) {
        ExchangeApplication application = getOwnedApplication(applicationId, userId);
        requireApplied(application);
        if (exchangeApplicationRepository.existsByPostIdAndStatusIn(application.getPostId(), IN_PROGRESS_OR_DONE)) {
            throw new BusinessException(PostErrorCode.ANOTHER_APPLICATION_ALREADY_ACCEPTED);
        }
        application.accept();
    }

    /** 글쓴이만 가능. 대기중(APPLIED)인 신청을 거절하거나, 이미 수락한 신청을 무를 때도 씁니다. */
    @Transactional
    public void reject(Long applicationId, Long userId) {
        ExchangeApplication application = getOwnedApplication(applicationId, userId);
        requireReversible(application);
        application.reject();
    }

    /**
     * 완료 처리는 글쓴이만 가능합니다(신청자는 못 함). 부모 게시글의 ExchangeDetail도 같이 완료 처리합니다.
     * ExchangeDetail을 Post와 한 번에 조회해서(join fetch) 소유자 확인용 Post를 따로 조회하지 않습니다.
     */
    @Transactional
    public void complete(Long applicationId, Long userId) {
        ExchangeApplication application = getApplication(applicationId);

        ExchangeDetail detail = exchangeDetailRepository.findWithPostByPostId(application.getPostId())
                .orElseThrow(() -> new BusinessException(PostErrorCode.POST_NOT_FOUND));
        if (!detail.getPost().isOwnedBy(userId)) {
            throw new BusinessException(PostErrorCode.NOT_POST_OWNER);
        }
        if (!application.isAccepted()) {
            throw new BusinessException(PostErrorCode.EXCHANGE_APPLICATION_INVALID_STATUS);
        }
        if (detail.isCompleted()) {
            throw new BusinessException(PostErrorCode.EXCHANGE_ALREADY_COMPLETED);
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
        requireReversible(application);
        application.cancel();
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

    private void requireApplied(ExchangeApplication application) {
        if (!application.isApplied()) {
            throw new BusinessException(PostErrorCode.EXCHANGE_APPLICATION_INVALID_STATUS);
        }
    }

    /** reject()/cancel()이 공유하는 가드. 대기중이거나 수락된 상태에서만 되돌릴 수 있습니다. */
    private void requireReversible(ExchangeApplication application) {
        if (!application.isApplied() && !application.isAccepted()) {
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
