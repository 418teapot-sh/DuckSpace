package com.duckspace.domain.user.service;

import com.duckspace.domain.user.dto.response.UserSearchResponse;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.entity.UserSearchHistory;
import com.duckspace.domain.user.exception.UserErrorCode;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.domain.user.repository.UserSearchHistoryRepository;
import com.duckspace.global.exception.BusinessException;
import com.duckspace.global.support.Paging;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_HISTORY_SIZE = 3;

    private final UserRepository userRepository;
    private final UserSearchHistoryRepository searchHistoryRepository;

    /** 닉네임으로 유저를 찾습니다. keyword가 비어 있으면 빈 목록입니다. */
    public List<UserSearchResponse> search(String keyword, Integer limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return userRepository.searchByNickname(
                        escapeLike(keyword.trim()), PageRequest.of(0, Paging.normalize(limit, DEFAULT_LIMIT, MAX_LIMIT)))
                .stream()
                .map(UserSearchResponse::from)
                .toList();
    }

    /** 검색 내역. 최근 클릭한 순으로 최대 {@value #MAX_HISTORY_SIZE}개입니다. */
    public List<UserSearchResponse> getHistory(Long searcherId) {
        return searchHistoryRepository
                .findBySearcherIdOrderByIdDesc(searcherId, PageRequest.of(0, MAX_HISTORY_SIZE))
                .stream()
                .map(history -> UserSearchResponse.from(history.getSearchedUser()))
                .toList();
    }

    /**
     * 검색 결과 클릭을 내역에 기록합니다. 자기 자신은 기록하지 않습니다(검색 결과에 본인이 뜰 수는 있지만
     * "이전 검색 내역"에 자기 자신이 남는 건 의미가 없어서 조용히 무시합니다).
     *
     * <p>이미 있던 항목이면 지우고 다시 넣어서 맨 위로 올리고, {@value #MAX_HISTORY_SIZE}개를
     * 넘기면 가장 오래된 항목을 지웁니다.
     */
    @Transactional
    public void record(Long searcherId, Long targetUserId) {
        if (searcherId.equals(targetUserId)) {
            return;
        }
        searchHistoryRepository.deleteBySearcherIdAndSearchedUserId(searcherId, targetUserId);

        User searcher = getUser(searcherId);
        User target = getUser(targetUserId);
        searchHistoryRepository.save(UserSearchHistory.of(searcher, target));

        if (searchHistoryRepository.countBySearcherId(searcherId) > MAX_HISTORY_SIZE) {
            searchHistoryRepository.findFirstBySearcherIdOrderByIdAsc(searcherId)
                    .ifPresent(searchHistoryRepository::delete);
        }
    }

    /** 검색 내역 전체 삭제. */
    @Transactional
    public void clearHistory(Long searcherId) {
        searchHistoryRepository.deleteAllBySearcherId(searcherId);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private static String escapeLike(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
