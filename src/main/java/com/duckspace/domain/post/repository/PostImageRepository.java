package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    List<PostImage> findByPost_IdOrderBySortOrderAsc(Long postId);

    void deleteByPost_Id(Long postId);
}
