package com.duckspace.domain.popup.repository;

import com.duckspace.domain.popup.entity.Popup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PopupRepository extends JpaRepository<Popup, Long> {

    List<Popup> findAllByOrderByStartDateAsc();

    /** 홈 화면 "다가오는 팝업" 섹션용 — 종료된 팝업은 제외합니다. */
    List<Popup> findAllByEndDateGreaterThanEqualOrderByStartDateAsc(LocalDate today);
}