package com.duckspace.domain.banner.repository;

import com.duckspace.domain.banner.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    List<Banner> findAllByOrderBySortOrderAsc();

    List<Banner> findAllByActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderBySortOrderAsc(
            LocalDateTime startAt, LocalDateTime endAt);
}