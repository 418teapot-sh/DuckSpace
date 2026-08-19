package com.duckspace.domain.user.service;

import com.duckspace.domain.user.dto.response.UserSearchResponse;
import com.duckspace.domain.user.exception.UserErrorCode;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.domain.user.repository.UserSearchHistoryRepository;
import com.duckspace.global.exception.BusinessException;
import com.duckspace.global.support.LikeEscaper;
import com.duckspace.global.support.Paging;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_HISTORY_SIZE = 3;
    private static final int MAX_ATTEMPTS = 8;
    private static final long RETRY_BACKOFF_MILLIS = 20;

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
     * 으로 올라오기 때문에, 잡은 뒤 재확인해서 구분합니다({@code FollowService.follow()}와 동일한
     * 이유·패턴). 이미 있으면 동시 요청이 먼저 넣은 것이므로 성공으로 보고, 없으면 진짜 실패이므로
     * 예외를 던집니다 — 여기서 구분 안 하고 무조건 무시하면, 탈퇴 같은 기능이 생겼을 때 저장은
     * 안 됐는데 204로 성공 응답하는 상황이 생깁니다.
     *
     * <p>재확인은 {@code searchHistoryRepository}가 아니라 {@link UserSearchHistoryWriter#exists}
     * (REQUIRES_NEW)로 합니다. 이 메서드(record) 자체가 이미 트랜잭션 안이라, 여기서 바로 재조회하면
     * MySQL REPEATABLE READ 스냅샷이 이 트랜잭션의 최초 조회(바로 위 {@code existsById}) 시점에
     * 고정돼 있어서 방금 다른 트랜잭션이 커밋한 행을 못 보고 오탐(USER_NOT_FOUND)이 날 수 있습니다.
     *
     * <p>같은 searcher에 서로 다른 target으로 여러 요청이 동시에 들어오면(delete/insert/트리밍이
     * 전부 같은 searcher의 행들을 건드리므로) MySQL이 <b>데드락으로 판단해 한쪽을 강제로
     * 롤백</b>시킬 수 있습니다({@link TransientDataAccessException}, 예: 락 대기 데드락). 이건
     * 버그가 아니라 InnoDB의 정상적인 데드락 해소 동작이고, 진 쪽이 살짝 쉬었다 재시도하면
     * 대부분 통과합니다(먼저 커밋한 트랜잭션들이 락을 놓을 시간을 벌어줌) — 그래서
     * {@value #MAX_ATTEMPTS}번까지, 매번 짧게 쉬면서 재시도합니다.
     *
     * <p>이 메서드는 일부러 <b>트랜잭션을 안 씁니다</b>({@code NOT_SUPPORTED}) — 쓰기는 전부
     * REQUIRES_NEW writer가 자기 트랜잭션에서 처리해서 바깥 트랜잭션이 지키는 원자성이 없는데,
     * 트랜잭션을 걸어두면 재시도 사이의 {@code sleep} 동안에도 커넥션을 쥔 채로 대기하게 됩니다.
     * 데드락이 잦은 상황(=커넥션이 가장 아쉬운 상황)에 요청당 커넥션을 하나 더 오래 붙잡는
     * 셈이라 트랜잭션 자체를 뗐습니다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void record(Long searcherId, Long targetUserId) {
        if (searcherId.equals(targetUserId)) {
            return;
        }
        if (!userRepository.existsById(searcherId) || !userRepository.existsById(targetUserId)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                searchHistoryWriter.replace(searcherId, targetUserId, MAX_HISTORY_SIZE);
                return;
            } catch (DataIntegrityViolationException e) {
                if (!searchHistoryWriter.exists(searcherId, targetUserId)) {
                    throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
                }
                // 동시 요청이 같은 조합을 먼저 넣은 경우 — 이미 원하는 상태이므로 무시합니다.
                return;
            } catch (TransientDataAccessException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                try {
                    Thread.sleep(RETRY_BACKOFF_MILLIS * attempt);
                } catch (InterruptedException ie) {
                    // 인터럽트 중엔 backoff 없이 재시도를 몰아치는 대신 바로 포기합니다.
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** 검색 내역 전체 삭제. */
    @Transactional
    public void clearHistory(Long searcherId) {
        searchHistoryRepository.deleteAllBySearcherId(searcherId);
    }
}
