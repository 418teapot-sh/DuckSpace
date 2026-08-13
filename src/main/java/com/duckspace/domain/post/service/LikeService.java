package com.duckspace.domain.post.service;

import com.duckspace.domain.post.entity.PostLike;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.post.repository.PostLikeRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostService postService;

    /** 좋아요 등록. 더블클릭 등으로 두 요청이 동시에 들어와도 유니크 제약으로 하나만 남습니다. */
    @Transactional
    public void like(Long userId, Long postId) {
        postService.getPost(postId);
        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new BusinessException(PostErrorCode.ALREADY_LIKED);
        }

        try {
            postLikeRepository.saveAndFlush(new PostLike(postId, userId));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(PostErrorCode.ALREADY_LIKED);
        }
    }

    /** 좋아요 취소. 이미 취소된 상태여도 에러 없이 넘어갑니다(멱등). */
    @Transactional
    public void unlike(Long userId, Long postId) {
        postLikeRepository.findByPostIdAndUserId(postId, userId)
                .ifPresent(postLikeRepository::delete);
    }
}
