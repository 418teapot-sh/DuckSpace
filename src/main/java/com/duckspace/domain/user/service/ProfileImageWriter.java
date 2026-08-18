package com.duckspace.domain.user.service;

import com.duckspace.domain.exhibition.image.ImageCleanup;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.exception.UserErrorCode;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 이미지 반영만 담당하는 짧은 트랜잭션.
 *
 * <p>{@link ProfileImageService}가 자기 자신의 {@code @Transactional} 메서드를 직접 부르면
 * (self-invocation) 스프링 프록시를 안 거쳐서 트랜잭션이 조용히 안 걸립니다 — 그러면
 * {@link User}가 트랜잭션 밖에서 detached 상태로 수정만 되고 DB엔 반영되지 않습니다.
 * 이 문제를 피하려고 별도 빈으로 뺐습니다({@code ExhibitionItemStatusWriter}와 같은 이유).
 */
@Service
@RequiredArgsConstructor
public class ProfileImageWriter {

    private final ImageCleanup imageCleanup;
    private final UserRepository userRepository;

    @Transactional
    public void apply(Long userId, String imageUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        String previousImageUrl = user.replaceProfileImage(imageUrl);
        imageCleanup.deleteAfterCommit(previousImageUrl);
    }
}
