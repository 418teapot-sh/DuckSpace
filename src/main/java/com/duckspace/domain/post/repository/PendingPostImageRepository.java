package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.PendingPostImage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PendingPostImageRepository extends JpaRepository<PendingPostImage, Long> {

    /** 글 작성 요청이 실제로 이 URL들을 썼다는 뜻이므로, "아직 안 쓰였다" 표시를 지웁니다. */
    void deleteByImageUrlIn(Collection<String> imageUrls);

    /** 정리 대상 후보. 한 번에 너무 많이 지우지 않도록 호출하는 쪽에서 Pageable로 배치 크기를 제한하세요. */
    List<PendingPostImage> findByCreatedAtBefore(LocalDateTime cutoff, Pageable pageable);
}
