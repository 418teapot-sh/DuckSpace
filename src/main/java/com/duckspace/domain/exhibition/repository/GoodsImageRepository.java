package com.duckspace.domain.exhibition.repository;

import com.duckspace.domain.exhibition.entity.GoodsImage;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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
     * ({@code ExhibitionItemRepository} 와 같은 패턴)
     *
     * <p>소유 조건을 쿼리에 넣은 이유: id 만으로 잠그면 남의 id 를 추측해 호출하는 것만으로
     * 남의 행에 락 경합을 걸 수 있습니다. 조건에 안 걸리면 잠기지 않습니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GoodsImage g where g.id = :imageId and g.userId = :userId")
    Optional<GoodsImage> findOwnedForUpdate(@Param("imageId") Long imageId, @Param("userId") Long userId);

    /**
     * 백그라운드 결과 기록용 잠금 조회. 소유 확인이 필요 없는 내부 경로 전용입니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GoodsImage g where g.id = :imageId")
    Optional<GoodsImage> findByIdForUpdate(@Param("imageId") Long imageId);

    /**
     * {@code updatedAt} 만 지금 시각으로 강제 갱신합니다.
     *
     * <p>이미 PENDING 인 행에 {@code markPending()} 을 불러도 바뀐 값이 없어 Hibernate 가
     * UPDATE 를 생략하고, 방치 판정 기준인 {@code updatedAt} 이 그대로 남습니다. 그러면
     * 방금 재시도를 받아줬는데도 계속 "방치됨" 으로 보여 연타로 중복 재처리가 접수됩니다.
     * 재시도를 접수할 때 이걸 불러 방치 시계를 확실히 되감습니다.
     */
    @Modifying
    @Query("update GoodsImage g set g.updatedAt = :now where g.id = :imageId")
    void touchUpdatedAt(@Param("imageId") Long imageId, @Param("now") LocalDateTime now);

    /** 삭제 후보 중 <b>보관함이 소유한 URL</b> 만 골라냅니다. ({@code ImageCleanup} 전용, in 절 배치) */
    @Query("select g.imageUrl from GoodsImage g where g.imageUrl in :urls")
    List<String> findReferencedUrls(@Param("urls") Collection<String> urls);
}
