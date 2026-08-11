package com.duckspace.domain.exhibition.repository;

import com.duckspace.domain.exhibition.entity.ExhibitionLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExhibitionLikeRepository extends JpaRepository<ExhibitionLike, Long> {

    Optional<ExhibitionLike> findByExhibition_IdAndUserId(Long exhibitionId, Long userId);
}