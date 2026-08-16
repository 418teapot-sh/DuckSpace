package com.duckspace.domain.user.repository;

import com.duckspace.domain.user.entity.Follow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    void deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);

    long countByFollowingId(Long userId);

    long countByFollowerId(Long userId);

    /**
     * userId를 팔로우하는 사람 목록(팔로워)을 최신순(id desc)으로 커서 페이지네이션합니다.
     * cursor가 null이면 최신부터, 값이 있으면 그보다 id가 작은 것부터 내려줍니다.
     * follower를 fetch join해서 N+1을 피합니다.
     */
    @Query("""
            select f from Follow f
            join fetch f.follower
            where f.following.id = :userId
              and (:cursor is null or f.id < :cursor)
            order by f.id desc
            """)
    List<Follow> findFollowersByFollowingId(@Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);

    /** userId가 팔로우하는 사람 목록(팔로잉)을 최신순으로 커서 페이지네이션합니다. */
    @Query("""
            select f from Follow f
            join fetch f.following
            where f.follower.id = :userId
              and (:cursor is null or f.id < :cursor)
            order by f.id desc
            """)
    List<Follow> findFollowingByFollowerId(@Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);
}
