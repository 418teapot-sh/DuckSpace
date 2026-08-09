package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
}