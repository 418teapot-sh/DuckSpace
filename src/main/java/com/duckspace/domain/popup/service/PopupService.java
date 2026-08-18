package com.duckspace.domain.popup.service;

import com.duckspace.domain.popup.client.OpenAiSummaryClient;
import com.duckspace.domain.popup.dto.request.PopupCreateRequest;
import com.duckspace.domain.popup.dto.request.PopupUpdateRequest;
import com.duckspace.domain.popup.dto.response.PopupResponse;
import com.duckspace.domain.popup.dto.response.PopupSummaryResponse;
import com.duckspace.domain.popup.entity.Popup;
import com.duckspace.domain.popup.exception.PopupErrorCode;
import com.duckspace.domain.popup.repository.PopupRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PopupService {

    private final PopupRepository popupRepository;
    private final OpenAiSummaryClient openAiSummaryClient;

    public List<PopupSummaryResponse> getPopups() {
        return popupRepository.findAllByOrderByStartDateAsc()
                .stream()
                .map(PopupSummaryResponse::from)
                .toList();
    }

    /** 홈 화면 "다가오는 팝업" 섹션용 — 종료된 팝업은 제외합니다. */
    public List<PopupSummaryResponse> getUpcomingPopups() {
        return popupRepository.findAllByEndDateGreaterThanEqualOrderByStartDateAsc(LocalDate.now())
                .stream()
                .map(PopupSummaryResponse::from)
                .toList();
    }

    public PopupResponse getPopup(Long popupId) {
        return PopupResponse.from(getPopupOrThrow(popupId));
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
                aiSummary
        );
        return PopupResponse.from(popup);
    }

    @Transactional
    public void deletePopup(Long popupId) {
        popupRepository.delete(getPopupOrThrow(popupId));
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(PopupErrorCode.INVALID_POPUP_PERIOD);
        }
    }

    private Popup getPopupOrThrow(Long popupId) {
        return popupRepository.findById(popupId)
                .orElseThrow(() -> new BusinessException(PopupErrorCode.POPUP_NOT_FOUND));
    }
}