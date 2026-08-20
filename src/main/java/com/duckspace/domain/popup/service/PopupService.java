package com.duckspace.domain.popup.service;

import com.duckspace.domain.popup.client.OpenAiSummaryClient;
import com.duckspace.domain.popup.dto.request.PopupCreateRequest;
import com.duckspace.domain.popup.dto.request.PopupUpdateRequest;
import com.duckspace.domain.popup.dto.response.PopupResponse;
import com.duckspace.domain.popup.dto.response.PopupSummaryResponse;
import com.duckspace.domain.popup.entity.Popup;
import com.duckspace.domain.popup.entity.PopupStatus;
import com.duckspace.domain.popup.exception.PopupErrorCode;
import com.duckspace.domain.popup.repository.PopupLikeRepository;
import com.duckspace.domain.popup.repository.PopupRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PopupService {

    private final PopupRepository popupRepository;
    private final OpenAiSummaryClient openAiSummaryClient;
    private final PopupLikeRepository popupLikeRepository;

    public List<PopupSummaryResponse> getPopups(Long viewerId) {
        List<Popup> popups = popupRepository.findAllByOrderByStartDateAsc();
        Set<Long> likedIds = likedPopupIds(viewerId, popups);
        return popups.stream()
                .map(popup -> PopupSummaryResponse.from(popup, likedIds.contains(popup.getId())))
                .toList();
    }

    /**
     * 홈 화면 "다가오는 팝업" 섹션용 — 종료된 팝업은 제외합니다.
     *
     * <p>뺄지 말지는 <b>응답에 실리는 {@code status} 하나만</b> 보고 정합니다. 예전에는
     * SQL 로 {@code end_date >= 오늘} 을 걸고 {@code status} 는 자바에서 따로 계산했는데,
     * 배포 환경에서 두 날짜가 하루 어긋나 <b>{@code status} 가 {@code ENDED} 인 팝업이
     * 목록에 그대로 남는</b> 일이 있었습니다(#114). 기준이 하나뿐이면 그럴 수 없습니다.
     *
     * <p>아직 시작 안 한 {@code UPCOMING} 은 남깁니다. 섹션 이름이 "다가오는 팝업" 이라
     * 그게 빠지면 안 됩니다.
     */
    public List<PopupSummaryResponse> getUpcomingPopups(Long viewerId) {
        List<Popup> popups = popupRepository.findAllByOrderByStartDateAsc();
        Set<Long> likedIds = likedPopupIds(viewerId, popups);
        return popups.stream()
                .map(popup -> PopupSummaryResponse.from(popup, likedIds.contains(popup.getId())))
                .filter(popup -> popup.status() != PopupStatus.ENDED)
                .toList();
    }

    public PopupResponse getPopup(Long popupId, Long viewerId) {
        Popup popup = getPopupOrThrow(popupId);
        boolean liked = viewerId != null && popupLikeRepository.existsByPopupIdAndUserId(popupId, viewerId);
        return PopupResponse.from(popup, liked);
    }

    public List<PopupSummaryResponse> getAllPopupsForAdmin() {
        return popupRepository.findAllByOrderByStartDateAsc()
                .stream()
                .map(PopupSummaryResponse::from)
                .toList();
    }

    @Transactional
    public PopupResponse createPopup(PopupCreateRequest request) {
        validatePeriod(request.startDate(), request.endDate());

        String aiSummary = openAiSummaryClient.summarizeSchedule(
                request.title(), request.description(), request.location(),
                request.startDate(), request.endDate());

        Popup popup = Popup.builder()
                .title(request.title())
                .imageUrl(request.imageUrl())
                .description(request.description())
                .location(request.location())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .aiSummary(aiSummary)
                .benefitImageUrl(request.benefitImageUrl())
                .benefitDescription(request.benefitDescription())
                .operatingHours(request.operatingHours())
                .build();

        return PopupResponse.from(popupRepository.save(popup));
    }

    @Transactional
    public PopupResponse updatePopup(Long popupId, PopupUpdateRequest request) {
        validatePeriod(request.startDate(), request.endDate());
        Popup popup = getPopupOrThrow(popupId);

        String aiSummary = openAiSummaryClient.summarizeSchedule(
                request.title(), request.description(), request.location(),
                request.startDate(), request.endDate());

        popup.update(
                request.title(),
                request.imageUrl(),
                request.description(),
                request.location(),
                request.startDate(),
                request.endDate(),
                aiSummary,
                request.benefitImageUrl(),
                request.benefitDescription(),
                request.operatingHours()
        );
        return PopupResponse.from(popup);
    }

    @Transactional
    public void deletePopup(Long popupId) {
        Popup popup = getPopupOrThrow(popupId);
        popupLikeRepository.deleteByPopupId(popup.getId());
        popupRepository.delete(popup);
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(PopupErrorCode.INVALID_POPUP_PERIOD);
        }
    }

    /** 패키지 프라이빗 — 존재 확인만 필요한 {@link PopupLikeService}에서도 재사용합니다. */
    Popup getPopupOrThrow(Long popupId) {
        return popupRepository.findById(popupId)
                .orElseThrow(() -> new BusinessException(PopupErrorCode.POPUP_NOT_FOUND));
    }

    private Set<Long> likedPopupIds(Long viewerId, List<Popup> popups) {
        if (viewerId == null || popups.isEmpty()) {
            return Set.of();
        }
        List<Long> popupIds = popups.stream().map(Popup::getId).toList();
        return new HashSet<>(popupLikeRepository.findLikedPopupIds(viewerId, popupIds));
    }
}
