package com.duckspace.domain.popup.service;

import com.duckspace.domain.popup.entity.Popup;
import com.duckspace.domain.popup.entity.PopupLike;
import com.duckspace.domain.popup.repository.PopupLikeRepository;
import com.duckspace.domain.popup.repository.PopupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 INSERT 만 <b>별도 트랜잭션</b>에서 수행합니다.
 *
 * <p>같은 트랜잭션 안에서 유니크 제약 위반을 잡고 넘어가면, 그 트랜잭션은 이미 rollback-only 로
 * 표시되어 있어 커밋 시점에 {@code UnexpectedRollbackException} 이 납니다.
 *
 * <p><b>별도 빈인 이유:</b> 같은 빈 안에서 호출하면 프록시를 타지 않아
 * {@code REQUIRES_NEW} 가 적용되지 않습니다.
 */
@Component
@RequiredArgsConstructor
class PopupLikeWriter {

    private final PopupLikeRepository popupLikeRepository;
    private final PopupRepository popupRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(Long popupId, Long userId) {
        Popup reference = popupRepository.getReferenceById(popupId);
        popupLikeRepository.saveAndFlush(new PopupLike(reference, userId));
    }
}
