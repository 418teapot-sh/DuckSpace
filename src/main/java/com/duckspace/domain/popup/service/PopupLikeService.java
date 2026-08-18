package com.duckspace.domain.popup.service;

import com.duckspace.domain.popup.dto.response.PopupSummaryResponse;
import com.duckspace.domain.popup.entity.Popup;
import com.duckspace.domain.popup.entity.PopupLike;
import com.duckspace.domain.popup.exception.PopupErrorCode;
import com.duckspace.domain.popup.repository.PopupLikeRepository;
import com.duckspace.domain.popup.repository.PopupRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PopupLikeService {

    private final PopupLikeRepository popupLikeRepository;
    private final PopupRepository popupRepository;
    private final PopupService popupService;

    /** 찜 등록. 더블클릭 등으로 두 요청이 동시에 들어와도 유니크 제약으로 하나만 남습니다. */
    @Transactional
    public void like(Long userId, Long popupId) {
        popupService.getPopupOrThrow(popupId);
        if (popupLikeRepository.existsByPopupIdAndUserId(popupId, userId)) {
            throw new BusinessException(PopupErrorCode.ALREADY_LIKED);
        }
        try {
            popupLikeRepository.saveAndFlush(new PopupLike(popupId, userId));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(PopupErrorCode.ALREADY_LIKED);
        }
    }

    /** 찜 취소. 이미 취소된 상태여도 에러 없이 넘어갑니다(멱등). */
    @Transactional
    public void unlike(Long userId, Long popupId) {
        popupLikeRepository.findByPopupIdAndUserId(popupId, userId)
                .ifPresent(popupLikeRepository::delete);
    }

    /** 내가 찜한 팝업 목록 — 최근 찜한 순. */
    public List<PopupSummaryResponse> getLikedPopups(Long userId) {
        List<Long> popupIds = popupLikeRepository.findByUserIdOrderByIdDesc(userId).stream()
                .map(PopupLike::getPopupId)
                .toList();

        Map<Long, Popup> byId = popupRepository.findAllById(popupIds).stream()
                .collect(Collectors.toMap(Popup::getId, popup -> popup));

        return popupIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(popup -> PopupSummaryResponse.from(popup, true))
                .toList();
    }
}
