package com.duckspace.domain.banner.service;

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
import com.duckspace.global.exception.BusinessException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BannerService {

    private final BannerRepository bannerRepository;

    public BannerListResponse getActiveBanners() {
        LocalDateTime now = LocalDateTime.now();
        List<BannerResponse> banners = bannerRepository
                .findAllByStartAtLessThanEqualAndEndAtGreaterThanEqualOrderBySortOrderAsc(now, now)
                .stream()
                .map(BannerResponse::from)
                .toList();
        return BannerListResponse.of(banners);
    }

    /*
    public List<BannerResponse> getAllBannersForAdmin() {
        return bannerRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(BannerResponse::from)
                .toList();
    }

    @Transactional
    public BannerResponse createBanner(BannerCreateRequest request) {
        validatePeriod(request.startAt(), request.endAt());

        Banner banner = Banner.builder()
                .imageUrl(request.imageUrl())
                .title(request.title())
                .popupId(request.popupId())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .sortOrder(request.sortOrder())
                .build();

        return BannerResponse.from(bannerRepository.save(banner));
    }

    @Transactional
    public BannerResponse updateBanner(Long bannerId, BannerUpdateRequest request) {
        validatePeriod(request.startAt(), request.endAt());

        Banner banner = getBannerOrThrow(bannerId);
        banner.update(
                request.imageUrl(),
                request.title(),
                request.popupId(),
                request.startAt(),
                request.endAt(),
                request.sortOrder()
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
    */
}