package com.duckspace.domain.user.service;

import com.duckspace.domain.user.dto.response.UserSearchResponse;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.exception.UserErrorCode;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.domain.user.repository.UserSearchHistoryRepository;
import com.duckspace.global.exception.BusinessException;
import com.duckspace.global.support.LikeEscaper;
import com.duckspace.global.support.Paging;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final UserSearchHistoryWriter searchHistoryWriter;

    /** 닉네임으로 유저를 찾습니다. keyword가 비어 있으면 빈 목록입니다. */
    public List<UserSearchResponse> search(String keyword, Integer limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return userRepository.searchByNickname(
                        LikeEscaper.escape(keyword.trim()),
                        PageRequest.of(0, Paging.normalize(limit, DEFAULT_LIMIT, MAX_LIMIT)))
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
     *
     * <p>삭제 + INSERT + 트리밍은 전부 {@link UserSearchHistoryWriter}가 별도 트랜잭션에서
     * 함께 수행합니다(이유는 그 클래스의 문서 참고 — 트랜잭션을 나누면 방금 커밋한 INSERT를
     * 트리밍 쿼리가 못 보는 문제가 있었습니다).
     *
     * <p>유니크 제약({@code uk_search_history_searcher_searched}) 위반과 FK 위반(존재 확인과
     * INSERT 사이에 searcher/target이 삭제됨)이 둘 다 같은 {@link DataIntegrityViolationException}
     * 으로 올라오기 때문에, 잡은 뒤 재확인해서 구분합니다({@code PopupLikeService.like()}와 동일한
     * 이유·패턴). 이미 있으면 동시 요청이 먼저 넣은 것이므로 성공으로 보고, 없으면 진짜 실패이므로
     * 예외를 던집니다 — 여기서 구분 안 하고 무조건 무시하면, 탈퇴 같은 기능이 생겼을 때 저장은
     * 안 됐는데 204로 성공 응답하는 상황이 생깁니다.
     */
    @Transactional
    public void record(Long searcherId, Long targetUserId) {
        if (searcherId.equals(targetUserId)) {
            return;
        }
        List<Long> foundIds = userRepository.findAllById(List.of(searcherId, targetUserId)).stream()
                .map(User::getId)
                .toList();
        if (foundIds.size() != 2) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        try {
            searchHistoryWriter.replace(searcherId, targetUserId, MAX_HISTORY_SIZE);
        } catch (DataIntegrityViolationException e) {
            if (!searchHistoryRepository.existsBySearcherIdAndSearchedUserId(searcherId, targetUserId)) {
                throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
            }
            // 동시 요청이 같은 조합을 먼저 넣은 경우 — 이미 원하는 상태이므로 무시합니다.
        }
    }

    /** 검색 내역 전체 삭제. */
    @Transactional
    public void clearHistory(Long searcherId) {
        searchHistoryRepository.deleteAllBySearcherId(searcherId);
    }
}
