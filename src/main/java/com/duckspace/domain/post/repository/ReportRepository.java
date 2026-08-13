package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.Report;
import com.duckspace.domain.post.entity.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);
}