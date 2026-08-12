package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.repository.ExhibitionLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExhibitionLikeService {

    private final ExhibitionLikeRepository exhibitionLikeRepository;
    private final ExhibitionLikeWriter exhibitionLikeWriter;
    private final ExhibitionService exhibitionService;

    /**
     * 좋아요. <b>여러 번 눌러도 결과가 같습니다.</b>
     *
     * <p>INSERT 는 {@link ExhibitionLikeWriter} 가 별도 트랜잭션에서 수행합니다.
     * 같은 트랜잭션에서 제약 위반을 삼키면 커밋 때 500 이 나기 때문입니다.
     */
    public void like(Long exhibitionId, Long userId) {
        exhibitionService.getExhibition(exhibitionId);
        try {
            exhibitionLikeWriter.insert(exhibitionId, userId);
        } catch (DataIntegrityViolationException e) {
            // 동시에 두 번 눌렸습니다. 이미 눌린 상태이므로 성공으로 봅니다.
        }
    }

    /**
     * 좋아요 취소. <b>누른 적이 없어도, 장식장이 이미 사라졌어도 성공으로 처리합니다.</b>
     *
     * <p>화면을 벗어날 때 방어적으로 호출하는 경우가 있어, 존재 확인으로 404 를 내면
     * "멱등하다" 는 약속이 깨집니다.
     */
    @Transactional
    public void unlike(Long exhibitionId, Long userId) {
        exhibitionLikeRepository.deleteByExhibitionIdAndUserId(exhibitionId, userId);
    }
}
