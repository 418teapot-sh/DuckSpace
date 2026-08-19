package com.duckspace.domain.user.entity;

import com.duckspace.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 유저 검색 결과를 클릭했을 때 남는 "이전 검색 내역" 한 건.
 *
 * <p>{@code (searcher_id, searched_user_id)} 유니크 제약은 같은 조합을 빠르게 두 번
 * 클릭했을 때(더블클릭, 클라이언트 재시도) 중복 행이 남는 걸 DB 제약으로 막기 위한 것입니다.
 * Follow/PopupLike와 같은 이유입니다.
 */
@Entity
@Table(name = "user_search_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_search_history_searcher_searched",
                columnNames = {"searcher_id", "searched_user_id"}),
        indexes = @Index(name = "idx_search_history_searcher", columnList = "searcher_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSearchHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "searcher_id", nullable = false, updatable = false)
    private User searcher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "searched_user_id", nullable = false, updatable = false)
    private User searchedUser;

    private UserSearchHistory(User searcher, User searchedUser) {
        this.searcher = searcher;
        this.searchedUser = searchedUser;
    }

    public static UserSearchHistory of(User searcher, User searchedUser) {
        return new UserSearchHistory(searcher, searchedUser);
    }
}
