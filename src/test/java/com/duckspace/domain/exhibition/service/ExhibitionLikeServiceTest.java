package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.domain.exhibition.repository.ExhibitionLikeRepository;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

/**
 * 좋아요는 <b>제약 위반 두 가지를 구분해야</b> 합니다.
 *
 * <p>유니크 제약(중복 클릭)은 성공으로 봐야 하고, FK 제약(장식장이 사라짐)은 실패로 봐야 하는데
 * 둘 다 {@link DataIntegrityViolationException} 으로 올라옵니다.
 */
@ExtendWith(MockitoExtension.class)
class ExhibitionLikeServiceTest {

    private static final Long EXHIBITION_ID = 10L;
    private static final Long USER_ID = 1L;

    @Mock
    private ExhibitionLikeRepository exhibitionLikeRepository;

    @Mock
    private ExhibitionLikeWriter exhibitionLikeWriter;

    @Mock
    private ExhibitionService exhibitionService;

    @InjectMocks
    private ExhibitionLikeService exhibitionLikeService;

    @Test
    @DisplayName("중복 클릭(유니크 제약)은 성공으로 처리한다")
    void 중복_클릭은_성공() {
        given(exhibitionService.getExhibition(EXHIBITION_ID)).willReturn(new Exhibition(USER_ID, "장식장", null));
        willThrow(new DataIntegrityViolationException("duplicate key"))
                .given(exhibitionLikeWriter).insert(EXHIBITION_ID, USER_ID);
        // 제약에 걸렸지만 행은 이미 있습니다 = 이미 눌러둔 상태.
        given(exhibitionLikeRepository.existsByExhibitionIdAndUserId(EXHIBITION_ID, USER_ID))
                .willReturn(true);

        exhibitionLikeService.like(EXHIBITION_ID, USER_ID);   // 예외 없음
    }

    @Test
    @DisplayName("조회와 INSERT 사이에 장식장이 삭제되면(FK 제약) 성공으로 속이지 않는다")
    void 사라진_장식장은_실패() {
        given(exhibitionService.getExhibition(EXHIBITION_ID)).willReturn(new Exhibition(USER_ID, "장식장", null));
        willThrow(new DataIntegrityViolationException("foreign key constraint fails"))
                .given(exhibitionLikeWriter).insert(EXHIBITION_ID, USER_ID);
        // 제약에 걸렸는데 행도 없습니다 = 좋아요가 기록되지 않았습니다.
        given(exhibitionLikeRepository.existsByExhibitionIdAndUserId(EXHIBITION_ID, USER_ID))
                .willReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> exhibitionLikeService.like(EXHIBITION_ID, USER_ID));

        assertThat(exception.getErrorCode())
                .as("기록되지 않았는데 성공을 돌려주면 화면에는 눌린 것처럼 보입니다")
                .isEqualTo(ExhibitionErrorCode.EXHIBITION_NOT_FOUND);
    }
}
