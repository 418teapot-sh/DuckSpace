package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.PostHashtag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, Long> {

    List<PostHashtag> findByPost_Id(Long postId);

    void deleteByPost_Id(Long postId);
}
