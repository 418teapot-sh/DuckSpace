package com.duckspace.domain.user.repository;

import com.duckspace.domain.user.entity.UserSearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserSearchHistoryRepository extends JpaRepository<UserSearchHistory, Long> {

    /** searcherId 본인의 검색 내역을 최신 클릭순(id desc)으로 가져옵니다. searchedUser를 fetch join해서 N+1을 피합니다. */
    @Query("""
            select h from UserSearchHistory h
            join fetch h.searchedUser
            where h.searcher.id = :searcherId
            order by h.id desc
            """)
    List<UserSearchHistory> findBySearcherIdOrderByIdDesc(@Param("searcherId") Long searcherId, Pageable pageable);

    /**
     * 이미 검색한 적 있는 유저를 다시 클릭하면 기존 항목을 지우고 새로 추가해서 맨 위로 올립니다.
     *
     * <p>파생 {@code deleteBy...} 메서드(엔티티를 찾아서 하나씩 {@code entityManager.remove()})가
     * 아니라 <b>벌크 {@code @Modifying} 쿼리</b>입니다. 엔티티 remove는 삭제하려는 행이 그 사이
     * 다른 트랜잭션에 의해 이미 지워졌으면(같은 조합을 동시에 재클릭) Hibernate가 "삭제된 행 수가
     * 기대(1)와 다르다"며 {@code ObjectOptimisticLockingFailureException}을 던집니다 —
     * {@code @Version} 유무와 무관하게 항상 이렇습니다. 벌크 쿼리는 몇 건이 지워지든(0건 포함)
     * 예외 없이 조용히 끝나서, 동시 삭제 경합에 안전합니다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserSearchHistory h where h.searcher.id = :searcherId and h.searchedUser.id = :searchedUserId")
    void deleteBySearcherIdAndSearchedUserId(@Param("searcherId") Long searcherId,
                                              @Param("searchedUserId") Long searchedUserId);

    /**
     * INSERT가 유니크 제약 위반으로 실패했을 때, 동시 요청이 먼저 넣어둔 것인지(성공으로 봐도 됨)
     * 아니면 다른 이유(예: FK 위반)로 진짜 실패한 것인지 구분하는 데 씁니다.
     */
    boolean existsBySearcherIdAndSearchedUserId(Long searcherId, Long searchedUserId);

    /** 전체 삭제도 위와 같은 이유로 벌크 쿼리입니다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserSearchHistory h where h.searcher.id = :searcherId")
    void deleteAllBySearcherId(@Param("searcherId") Long searcherId);

    /**
     * 프로덕션 호출부는 없습니다 — 트리밍이 "count 확인 후 삭제"에서 "최신 N개 조회 후 벌크
     * 삭제"로 바뀌면서(3라운드 리뷰) 개수를 직접 셀 일이 없어졌습니다. 테스트에서 결과 검증용으로
     * 씁니다.
     */
    long countBySearcherId(Long searcherId);

    /** 트리밍용 — 최신 N개만 남기고 지울 때, 남길 id 목록을 먼저 뽑습니다. */
    @Query("select h.id from UserSearchHistory h where h.searcher.id = :searcherId order by h.id desc")
    List<Long> findIdsBySearcherIdOrderByIdDesc(@Param("searcherId") Long searcherId, Pageable pageable);

    /**
     * keepIds에 없는(=최신 N개에서 밀려난) 항목을 한 번에 지웁니다. 벌크 쿼리인 이유는
     * {@link #deleteBySearcherIdAndSearchedUserId} 문서 참고 — 서로 다른 target으로 동시에
     * record()가 호출되면 두 트랜잭션이 같은 "밀려난 행"을 동시에 지우려 할 수 있는데, 엔티티
     * remove 방식이면 진 쪽이 예외로 죽습니다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserSearchHistory h where h.searcher.id = :searcherId and h.id not in :keepIds")
    void deleteBySearcherIdAndIdNotIn(@Param("searcherId") Long searcherId, @Param("keepIds") List<Long> keepIds);
}
