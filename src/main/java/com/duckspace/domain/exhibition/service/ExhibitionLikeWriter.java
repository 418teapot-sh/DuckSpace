package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionLike;
import com.duckspace.domain.exhibition.repository.ExhibitionLikeRepository;
import com.duckspace.domain.exhibition.repository.ExhibitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 INSERT 만 <b>별도 트랜잭션</b>에서 수행합니다.
 *
 * <p>같은 트랜잭션 안에서 유니크 제약 위반을 잡고 넘어가면, 그 트랜잭션은 이미 rollback-only 로
 * 표시되어 있어 커밋 시점에 {@code UnexpectedRollbackException} 이 납니다. 동시에 두 번 누른
 * 사용자가 "멱등하게 성공" 대신 500 을 받게 됩니다.
 *
 * <p><b>별도 빈인 이유:</b> 같은 빈 안에서 호출하면 프록시를 타지 않아
 * {@code REQUIRES_NEW} 가 적용되지 않습니다.
 */
@Component
@RequiredArgsConstructor
class ExhibitionLikeWriter {

    private final ExhibitionLikeRepository exhibitionLikeRepository;
    private final ExhibitionRepository exhibitionRepository;

    /** 이미 눌러둔 상태면 아무 일도 하지 않습니다. 제약 위반은 호출부가 잡습니다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(Long exhibitionId, Long userId) {
        if (exhibitionLikeRepository.existsByExhibitionIdAndUserId(exhibitionId, userId)) {
            return;
        }
        Exhibition reference = exhibitionRepository.getReferenceById(exhibitionId);
        exhibitionLikeRepository.saveAndFlush(new ExhibitionLike(reference, userId));
    }
}
