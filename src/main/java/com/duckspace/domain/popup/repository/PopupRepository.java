package com.duckspace.domain.popup.repository;

import com.duckspace.domain.popup.entity.Popup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PopupRepository extends JpaRepository<Popup, Long> {

    List<Popup> findAllByOrderByStartDateAsc();
}
