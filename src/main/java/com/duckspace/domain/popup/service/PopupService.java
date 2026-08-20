package com.duckspace.domain.popup.service;

import com.duckspace.domain.popup.client.OpenAiSummaryClient;
import com.duckspace.domain.popup.dto.request.PopupCreateRequest;
import com.duckspace.domain.popup.dto.request.PopupUpdateRequest;
import com.duckspace.domain.popup.dto.response.PopupResponse;
import com.duckspace.domain.popup.dto.response.PopupSummaryResponse;
import com.duckspace.domain.popup.entity.Popup;
import com.duckspace.domain.popup.exception.PopupErrorCode;
import com.duckspace.domain.popup.repository.PopupLikeRepository;
import com.duckspace.domain.popup.repository.PopupRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duckspace.global.support.ServiceZone;

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

    /** 홈 화면 "다가오는 팝업" 섹션용 — 종료된 팝업은 제외합니다. */
    public List<PopupSummaryResponse> getUpcomingPopups(Long viewerId) {
        List<Popup> popups = popupRepository
                .findAllByEndDateGreaterThanEqualOrderByStartDateAsc(LocalDate.now(ServiceZone.ZONE));
        Set<Long> likedIds = likedPopupIds(viewerId, popups);
        return popups.stream()
                .map(popup -> PopupSummaryResponse.from(popup, likedIds.contains(popup.getId())))
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
                request.benefitDescription()
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
