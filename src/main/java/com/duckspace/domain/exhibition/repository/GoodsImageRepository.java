package com.duckspace.domain.exhibition.repository;

import com.duckspace.domain.exhibition.entity.GoodsImage;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GoodsImageRepository extends JpaRepository<GoodsImage, Long> {

    /** 보관함 최초 진입. 최신순입니다. */
    List<GoodsImage> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    /** 보관함 더보기. 커서보다 오래된 것만. */
    List<GoodsImage> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long cursor, Pageable pageable);

    /**
     * 재처리 접수용. <b>행을 잠그고</b> 읽습니다.
     *
     * <p>재시도 버튼을 빠르게 두 번 누르면 둘 다 FAILED 를 보고 통과해 같은 사진이 두 번
     * 처리됩니다(remove.bg 크레딧 2회). 잠그면 뒤에 온 요청이 PENDING 을 보고 물러납니다.
     * ({@code ExhibitionItemRepository.findByIdForUpdate} 와 같은 패턴)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GoodsImage g where g.id = :imageId")
    Optional<GoodsImage> findByIdForUpdate(@Param("imageId") Long imageId);

    /** 삭제 후보 중 <b>보관함이 소유한 URL</b> 만 골라냅니다. ({@code ImageCleanup} 전용, in 절 배치) */
    @Query("select g.imageUrl from GoodsImage g where g.imageUrl in :urls")
    List<String> findReferencedUrls(@Param("urls") Collection<String> urls);
}
