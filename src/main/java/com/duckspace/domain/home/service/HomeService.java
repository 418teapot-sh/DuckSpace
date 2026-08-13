package com.duckspace.domain.home.service;

import com.duckspace.domain.banner.service.BannerService;
import com.duckspace.domain.home.dto.response.HomeResponse;
import com.duckspace.domain.popup.service.PopupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private final BannerService bannerService;
    private final PopupService popupService;

    public HomeResponse getHome() {
        return new HomeResponse(
                bannerService.getActiveBanners().banners(),
                popupService.getUpcomingPopups()
        );
    }
}