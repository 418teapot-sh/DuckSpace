package com.duckspace.domain.popup.repository;

import com.duckspace.domain.popup.entity.Popup;
import com.duckspace.domain.popup.entity.PopupLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PopupLikeRepository extends JpaRepository<PopupLike, Long> {

    Optional<PopupLike> findByPopupIdAndUserId(Long popupId, Long userId);

    boolean existsByPopupIdAndUserId(Long popupId, Long userId);

    /** 팝업 삭제 시 같이 정리합니다 — 안 지우면 popup_like에 고아 행이 영구히 남습니다. */
    void deleteByPopupId(Long popupId);

    /** 목록 화면에서 카드마다 하트 표시를 채우기 위한 배치 조회. N+1 방지용. */
    @Query("select l.popup.id from PopupLike l where l.userId = :userId and l.popup.id in :popupIds")
    List<Long> findLikedPopupIds(@Param("userId") Long userId, @Param("popupIds") List<Long> popupIds);

    /** 위시리스트 화면용 — 최근 찜한 순. */
    @Query("select l.popup from PopupLike l where l.userId = :userId order by l.id desc")
    List<Popup> findLikedPopups(@Param("userId") Long userId);
}
