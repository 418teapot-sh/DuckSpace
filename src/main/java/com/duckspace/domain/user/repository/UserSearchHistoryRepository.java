package com.duckspace.domain.user.repository;

import com.duckspace.domain.user.entity.UserSearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserSearchHistoryRepository extends JpaRepository<UserSearchHistory, Long> {

    /** searcherId 본인의 검색 내역을 최신 클릭순(id desc)으로 가져옵니다. searchedUser를 fetch join해서 N+1을 피합니다. */
    @Query("""
            select h from UserSearchHistory h
            join fetch h.searchedUser
            where h.searcher.id = :searcherId
            order by h.id desc
            """)
    List<UserSearchHistory> findBySearcherIdOrderByIdDesc(@Param("searcherId") Long searcherId, Pageable pageable);

    /** 이미 검색한 적 있는 유저를 다시 클릭하면 기존 항목을 지우고 새로 추가해서 맨 위로 올립니다. */
    void deleteBySearcherIdAndSearchedUserId(Long searcherId, Long searchedUserId);

    void deleteAllBySearcherId(Long searcherId);

    long countBySearcherId(Long searcherId);

    /** 3개를 넘기면 가장 오래된(id가 가장 작은) 항목을 지우는 데 씁니다. */
    Optional<UserSearchHistory> findFirstBySearcherIdOrderByIdAsc(Long searcherId);
}
