package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.domain.exhibition.repository.ExhibitionLikeRepository;
import com.duckspace.global.exception.BusinessException;
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
            // 여기로 오는 경우가 두 가지인데 결과가 정반대라 구분해야 합니다.
            //   1) 유니크 제약 — 동시에 두 번 눌렸습니다. 이미 눌린 상태이므로 성공.
            //   2) FK 제약 — 위 존재 확인과 INSERT 사이에 장식장이 삭제됐습니다. 좋아요는
            //      기록되지 않았는데도 성공을 돌려주면, 화면에는 눌린 것처럼 보입니다.
            // 예외 메시지로 가르는 건 DB 마다 달라 깨지기 쉬우므로, 결과를 직접 확인합니다.
            if (!exhibitionLikeRepository.existsByExhibitionIdAndUserId(exhibitionId, userId)) {
                throw new BusinessException(ExhibitionErrorCode.EXHIBITION_NOT_FOUND);
            }
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
