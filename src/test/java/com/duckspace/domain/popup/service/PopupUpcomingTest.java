package com.duckspace.domain.popup.service;

import com.duckspace.domain.popup.client.OpenAiSummaryClient;
import com.duckspace.domain.popup.dto.response.PopupSummaryResponse;
import com.duckspace.domain.popup.entity.Popup;
import com.duckspace.domain.popup.entity.PopupStatus;
import com.duckspace.domain.popup.repository.PopupLikeRepository;
import com.duckspace.domain.popup.repository.PopupRepository;
import com.duckspace.global.support.ServiceZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 홈 "다가오는 팝업" 은 <b>목록에 넣을지와 {@code status} 가 같은 기준을 봐야</b> 합니다.
 *
 * <p>예전에는 SQL 로 {@code end_date >= 오늘} 을 걸고 {@code status} 는 자바에서 따로
 * 계산했는데, 배포 환경에서 두 날짜가 하루 어긋나 {@code status} 가 {@code ENDED} 인
 * 팝업이 목록에 그대로 남았습니다(#114). 지금은 {@code status} 하나만 봅니다.
 */
@ExtendWith(MockitoExtension.class)
class PopupUpcomingTest {

    private static final LocalDate TODAY = LocalDate.now(ServiceZone.ZONE);

    @Mock
    private PopupRepository popupRepository;

    @Mock
    private OpenAiSummaryClient openAiSummaryClient;

    @Mock
    private PopupLikeRepository popupLikeRepository;

    @InjectMocks
    private PopupService popupService;

    private static long nextId = 1;

    /** 찜 조회가 id 로 대조하므로 영속 엔티티처럼 id 를 채워둡니다. */
    private static Popup popup(String title, LocalDate start, LocalDate end) {
        Popup popup = Popup.builder().title(title).startDate(start).endDate(end).build();
        ReflectionTestUtils.setField(popup, "id", nextId++);
        return popup;
    }

    @Test
    @DisplayName("어제 끝난 팝업은 목록에서 빠진다")
    void 종료된_팝업_제외() {
        Popup ended = popup("어제 끝남", TODAY.minusDays(14), TODAY.minusDays(1));
        Popup ongoing = popup("진행중", TODAY.minusDays(3), TODAY.plusDays(3));
        given(popupRepository.findAllByOrderByStartDateAsc()).willReturn(List.of(ended, ongoing));

        List<PopupSummaryResponse> result = popupService.getUpcomingPopups(null);

        assertThat(result).extracting(PopupSummaryResponse::title).containsExactly("진행중");
    }

    @Test
    @DisplayName("오늘 끝나는 팝업은 아직 남는다 — 오늘까지는 갈 수 있다")
    void 오늘_끝나는_팝업_포함() {
        Popup endsToday = popup("오늘까지", TODAY.minusDays(7), TODAY);
        given(popupRepository.findAllByOrderByStartDateAsc()).willReturn(List.of(endsToday));

        List<PopupSummaryResponse> result = popupService.getUpcomingPopups(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(PopupStatus.ONGOING);
    }

    @Test
    @DisplayName("아직 시작 안 한 팝업은 남는다 — 섹션 이름이 '다가오는 팝업' 이다")
    void 시작전_팝업_포함() {
        Popup upcoming = popup("다음주 시작", TODAY.plusDays(7), TODAY.plusDays(14));
        given(popupRepository.findAllByOrderByStartDateAsc()).willReturn(List.of(upcoming));

        List<PopupSummaryResponse> result = popupService.getUpcomingPopups(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(PopupStatus.UPCOMING);
    }

    @Test
    @DisplayName("응답에 실린 status 가 ENDED 인 항목은 하나도 없다")
    void 목록과_status_가_어긋나지_않는다() {
        given(popupRepository.findAllByOrderByStartDateAsc()).willReturn(List.of(
                popup("한참 전에 끝남", TODAY.minusDays(30), TODAY.minusDays(20)),
                popup("어제 끝남", TODAY.minusDays(10), TODAY.minusDays(1)),
                popup("진행중", TODAY.minusDays(1), TODAY.plusDays(1)),
                popup("시작 전", TODAY.plusDays(5), TODAY.plusDays(9))
        ));

        List<PopupSummaryResponse> result = popupService.getUpcomingPopups(null);

        assertThat(result).extracting(PopupSummaryResponse::status).doesNotContain(PopupStatus.ENDED);
        assertThat(result).extracting(PopupSummaryResponse::title)
                .containsExactly("진행중", "시작 전");
    }
}
