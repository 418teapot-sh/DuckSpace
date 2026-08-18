package com.duckspace.domain.popup.repository;

import com.duckspace.domain.popup.entity.PopupLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PopupLikeRepository extends JpaRepository<PopupLike, Long> {

    Optional<PopupLike> findByPopupIdAndUserId(Long popupId, Long userId);

    boolean existsByPopupIdAndUserId(Long popupId, Long userId);

    /** 목록 화면에서 카드마다 하트 표시를 채우기 위한 배치 조회. N+1 방지용. */
    @Query("select l.popupId from PopupLike l where l.userId = :userId and l.popupId in :popupIds")
    List<Long> findLikedPopupIds(@Param("userId") Long userId, @Param("popupIds") List<Long> popupIds);

    /** 위시리스트 화면용 — 최근 찜한 순 */
    List<PopupLike> findByUserIdOrderByIdDesc(Long userId);
}
