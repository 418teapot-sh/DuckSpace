package com.duckspace.domain.post.service;

import com.duckspace.domain.post.dto.request.ExchangeApplicationRequest;
import com.duckspace.domain.post.dto.response.ExchangeApplicationResponse;
import com.duckspace.domain.post.entity.BoardType;
import com.duckspace.domain.post.entity.ExchangeApplication;
import com.duckspace.domain.post.entity.ExchangeDetail;
import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.post.repository.ExchangeApplicationRepository;
import com.duckspace.domain.post.repository.ExchangeDetailRepository;
import com.duckspace.domain.post.repository.PostRepository;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 교환 신청→수락→완료 매칭. 후기/신뢰도 점수는 이 스코프에 없습니다(프론트 목업).
 * 권한 체크("글쓴이만")는 {@link PostService#getPost}로 얻은 {@link Post}의 {@link Post#isOwnedBy}를 재사용합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeApplicationService {

    private static final String FILTER_SENT = "sent";
    private static final String FILTER_RECEIVED = "received";

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

        ExchangeApplication application = exchangeApplicationRepository.save(
                new ExchangeApplication(postId, userId, request.offeredItemName(), request.offeredImageUrl(),
                        request.offeredBrand(), request.offeredCondition(), request.message()));
        return application.getId();
    }

    /** 해당 게시글에 달린 신청 목록. 글쓴이만 조회할 수 있습니다. */
    public List<ExchangeApplicationResponse> listByPost(Long postId, Long userId) {
        Post post = postService.getPost(postId);
        if (!post.isOwnedBy(userId)) {
            throw new BusinessException(PostErrorCode.NOT_POST_OWNER);
        }

        List<ExchangeApplication> applications = exchangeApplicationRepository.findByPostIdOrderByAppliedAtDesc(postId);
        Map<Long, String> nicknames = batchNicknames(applications);
        return applications.stream()
                .map(application -> toResponse(application, post.getTitle(), nicknames.get(application.getApplicantUserId())))
                .toList();
    }

    /** 내 신청함. filter는 sent(내가 신청한 것)/received(내 글에 들어온 신청)만 허용합니다. */
    public List<ExchangeApplicationResponse> listMine(Long userId, String filter) {
        List<ExchangeApplication> applications = findByFilter(userId, filter);
        if (applications.isEmpty()) {
            return List.of();
        }

        Map<Long, String> postTitles = batchPostTitles(applications);
        Map<Long, String> nicknames = batchNicknames(applications);
        return applications.stream()
                .map(application -> toResponse(application,
                        postTitles.get(application.getPostId()), nicknames.get(application.getApplicantUserId())))
                .toList();
    }

    @Transactional
    public void accept(Long applicationId, Long userId) {
        ExchangeApplication application = getApplication(applicationId);
        requirePostOwner(application, userId);
        requireApplied(application);
        application.accept();
    }

    @Transactional
    public void reject(Long applicationId, Long userId) {
        ExchangeApplication application = getApplication(applicationId);
        requirePostOwner(application, userId);
        requireApplied(application);
        application.reject();
    }

    /** 완료 처리는 글쓴이만 가능합니다(신청자는 못 함). 부모 게시글의 ExchangeDetail도 같이 완료 처리합니다. */
    @Transactional
    public void complete(Long applicationId, Long userId) {
        ExchangeApplication application = getApplication(applicationId);
        requirePostOwner(application, userId);
        if (!application.isAccepted()) {
            throw new BusinessException(PostErrorCode.EXCHANGE_APPLICATION_INVALID_STATUS);
        }
        application.complete();

        ExchangeDetail detail = exchangeDetailRepository.findById(application.getPostId())
                .orElseThrow(() -> new BusinessException(PostErrorCode.POST_NOT_FOUND));
        if (!detail.isCompleted()) {
            detail.complete();
        }
    }

    @Transactional
    public void cancel(Long applicationId, Long userId) {
        ExchangeApplication application = getApplication(applicationId);
        if (!application.isOwnedByApplicant(userId)) {
            throw new BusinessException(PostErrorCode.NOT_APPLICATION_OWNER);
        }
        requireApplied(application);
        application.cancel();
    }

    private List<ExchangeApplication> findByFilter(Long userId, String filter) {
        if (FILTER_SENT.equals(filter)) {
            return exchangeApplicationRepository.findByApplicantUserIdOrderByAppliedAtDesc(userId);
        }
        if (FILTER_RECEIVED.equals(filter)) {
            return exchangeApplicationRepository.findReceivedByUserId(userId);
        }
        throw new BusinessException(PostErrorCode.INVALID_APPLICATION_FILTER);
    }

    private ExchangeApplication getApplication(Long applicationId) {
        return exchangeApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(PostErrorCode.EXCHANGE_APPLICATION_NOT_FOUND));
    }

    private void requirePostOwner(ExchangeApplication application, Long userId) {
        Post post = postService.getPost(application.getPostId());
        if (!post.isOwnedBy(userId)) {
            throw new BusinessException(PostErrorCode.NOT_POST_OWNER);
        }
    }

    private void requireApplied(ExchangeApplication application) {
        if (!application.isApplied()) {
            throw new BusinessException(PostErrorCode.EXCHANGE_APPLICATION_INVALID_STATUS);
        }
    }

    private Map<Long, String> batchPostTitles(List<ExchangeApplication> applications) {
        List<Long> postIds = applications.stream().map(ExchangeApplication::getPostId).distinct().toList();
        return postRepository.findAllById(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Post::getTitle));
    }

    private Map<Long, String> batchNicknames(List<ExchangeApplication> applications) {
        List<Long> applicantIds = applications.stream().map(ExchangeApplication::getApplicantUserId).distinct().toList();
        return userRepository.findAllById(applicantIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
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
