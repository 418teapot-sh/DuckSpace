package com.duckspace.domain.banner.service;

import com.duckspace.domain.banner.client.BannerSummaryClient;
import com.duckspace.domain.banner.repository.BannerRepository;
import com.duckspace.domain.banner.dto.response.BannerListResponse;
import com.duckspace.domain.banner.dto.response.BannerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import com.duckspace.domain.banner.dto.request.BannerCreateRequest;
import com.duckspace.domain.banner.dto.request.BannerUpdateRequest;
import com.duckspace.domain.banner.entity.Banner;
import com.duckspace.domain.banner.exception.BannerErrorCode;
import com.duckspace.domain.popup.repository.PopupRepository;
import com.duckspace.global.exception.BusinessException;
import com.duckspace.global.support.ServiceZone;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BannerService {

    private final BannerRepository bannerRepository;
    private final PopupRepository popupRepository;
    private final BannerSummaryClient bannerSummaryClient;

    public BannerListResponse getActiveBanners() {
        // 서버(JVM) 기본 타임존에 기대지 않도록 명시적으로 Asia/Seoul 기준 현재 시각을 씁니다.
        LocalDateTime now = LocalDateTime.now(ServiceZone.ZONE);
        List<BannerResponse> banners = bannerRepository
                .findAllByActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderBySortOrderAsc(now, now)
                .stream()
                .map(BannerResponse::from)
                .toList();
        return BannerListResponse.of(banners);
    }

    public List<BannerResponse> getAllBannersForAdmin() {
        return bannerRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(BannerResponse::from)
                .toList();
    }

    @Transactional
    public BannerResponse createBanner(BannerCreateRequest request) {
        validatePeriod(request.startAt(), request.endAt());
        validatePopupExists(request.popupId());

        String aiSummary = bannerSummaryClient.summarizeBanner(
                request.title(), request.description(), request.startAt(), request.endAt());

        Banner banner = Banner.builder()
                .imageUrl(request.imageUrl())
                .title(request.title())
                .description(request.description())
                .aiSummary(aiSummary)
                .popupId(request.popupId())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .sortOrder(request.sortOrder())
                .active(request.active())
                .build();

        return BannerResponse.from(bannerRepository.save(banner));
    }

    @Transactional
    public BannerResponse updateBanner(Long bannerId, BannerUpdateRequest request) {
        validatePeriod(request.startAt(), request.endAt());
        validatePopupExists(request.popupId());
        Banner banner = getBannerOrThrow(bannerId);

        String aiSummary = bannerSummaryClient.summarizeBanner(
                request.title(), request.description(), request.startAt(), request.endAt());

        banner.update(
                request.imageUrl(),
                request.title(),
                request.description(),
                aiSummary,
                request.popupId(),
                request.startAt(),
                request.endAt(),
                request.sortOrder(),
                request.active()
        );
        return BannerResponse.from(banner);
    }

    @Transactional
    public void deleteBanner(Long bannerId) {
        bannerRepository.delete(getBannerOrThrow(bannerId));
    }

    private Banner getBannerOrThrow(Long bannerId) {
        return bannerRepository.findById(bannerId)
                .orElseThrow(() -> new BusinessException(BannerErrorCode.BANNER_NOT_FOUND));
    }

    private void validatePeriod(LocalDateTime startAt, LocalDateTime endAt) {
        if (endAt.isBefore(startAt)) {
            throw new BusinessException(BannerErrorCode.INVALID_BANNER_PERIOD);
        }
    }

    private void validatePopupExists(Long popupId) {
        if (!popupRepository.existsById(popupId)) {
            throw new BusinessException(BannerErrorCode.POPUP_NOT_FOUND);
        }
    }
}