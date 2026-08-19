package com.duckspace.domain.popup.service;

import com.duckspace.domain.popup.entity.Popup;
import com.duckspace.domain.popup.exception.PopupErrorCode;
import com.duckspace.domain.popup.repository.PopupLikeRepository;
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
import static org.mockito.Mockito.mock;

/**
 * 찜 등록은 <b>제약 위반 두 가지를 구분해야</b> 합니다.
 *
 * <p>유니크 제약(중복 클릭)은 ALREADY_LIKED, FK 제약(그 사이 팝업이 삭제됨)은
 * POPUP_NOT_FOUND — 둘 다 {@link DataIntegrityViolationException} 으로 올라오지만
 * 의미가 다릅니다.
 */
@ExtendWith(MockitoExtension.class)
class PopupLikeServiceTest {

    private static final Long POPUP_ID = 10L;
    private static final Long USER_ID = 1L;

    @Mock
    private PopupLikeRepository popupLikeRepository;

    @Mock
    private PopupLikeWriter popupLikeWriter;

    @Mock
    private PopupService popupService;

    @InjectMocks
    private PopupLikeService popupLikeService;

    @Test
    @DisplayName("중복 클릭(유니크 제약)은 ALREADY_LIKED로 응답한다")
    void 중복_클릭은_ALREADY_LIKED() {
        given(popupService.getPopupOrThrow(POPUP_ID)).willReturn(mock(Popup.class));
        willThrow(new DataIntegrityViolationException("duplicate key"))
                .given(popupLikeWriter).insert(POPUP_ID, USER_ID);
        // 제약에 걸렸지만 행은 이미 있습니다 = 이미 눌러둔 상태.
        given(popupLikeRepository.existsByPopupIdAndUserId(POPUP_ID, USER_ID))
                .willReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> popupLikeService.like(USER_ID, POPUP_ID));

        assertThat(exception.getErrorCode()).isEqualTo(PopupErrorCode.ALREADY_LIKED);
    }

    @Test
    @DisplayName("조회와 INSERT 사이에 팝업이 삭제되면(FK 제약) ALREADY_LIKED로 속이지 않는다")
    void 사라진_팝업은_POPUP_NOT_FOUND() {
        given(popupService.getPopupOrThrow(POPUP_ID)).willReturn(mock(Popup.class));
        willThrow(new DataIntegrityViolationException("foreign key constraint fails"))
                .given(popupLikeWriter).insert(POPUP_ID, USER_ID);
        // 제약에 걸렸는데 행도 없습니다 = 찜이 기록되지 않았습니다.
        given(popupLikeRepository.existsByPopupIdAndUserId(POPUP_ID, USER_ID))
                .willReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> popupLikeService.like(USER_ID, POPUP_ID));

        assertThat(exception.getErrorCode())
                .as("기록되지 않았는데 ALREADY_LIKED로 응답하면 클라이언트는 찜이 된 줄 알게 됩니다")
                .isEqualTo(PopupErrorCode.POPUP_NOT_FOUND);
    }
}
