package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionLike;
import com.duckspace.domain.exhibition.repository.ExhibitionLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExhibitionLikeService {

    private final ExhibitionLikeRepository exhibitionLikeRepository;
    private final ExhibitionService exhibitionService;

    /**
     * 좋아요. <b>여러 번 눌러도 결과가 같습니다.</b>
     *
     * <p>프론트가 현재 상태를 정확히 알지 못해도 안전하도록 멱등하게 만들었습니다.
     * 이미 눌렀으면 아무 일도 하지 않습니다.
     */
    @Transactional
    public void like(Long exhibitionId, Long userId) {
        Exhibition exhibition = exhibitionService.getExhibition(exhibitionId);

        if (exhibitionLikeRepository.existsByExhibitionIdAndUserId(exhibitionId, userId)) {
            return;
        }

        try {
            exhibitionLikeRepository.saveAndFlush(new ExhibitionLike(exhibition, userId));
        } catch (DataIntegrityViolationException e) {
            // 같은 사람이 동시에 두 번 눌렀습니다. 이미 눌린 상태이므로 성공으로 봅니다.
        }
    }

    /** 좋아요 취소. 누른 적 없어도 성공으로 처리합니다. */
    @Transactional
    public void unlike(Long exhibitionId, Long userId) {
        exhibitionService.getExhibition(exhibitionId);
        exhibitionLikeRepository.deleteByExhibitionIdAndUserId(exhibitionId, userId);
    }
}
