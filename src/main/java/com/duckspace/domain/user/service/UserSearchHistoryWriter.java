package com.duckspace.domain.user.service;

import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.entity.UserSearchHistory;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.domain.user.repository.UserSearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기존 항목 삭제 + INSERT + 최대 개수 트리밍을 <b>한 트랜잭션</b>에서, 그리고 호출자와는
 * <b>별도 트랜잭션</b>에서 수행합니다.
 *
 * <p>삭제와 INSERT를 반드시 같은 트랜잭션(같은 커넥션)에 둬야 합니다 — 분리하면 삭제가
 * 아직 커밋되지 않은 상태에서 INSERT가 실행돼(REQUIRES_NEW라 서로 다른 트랜잭션이 서로의
 * 미커밋 변경을 못 봄) 옛 행이 남아있는 것으로 보여 유니크 제약 위반이 나고, 그 사이 바깥
 * 트랜잭션이 커밋되면서 삭제만 반영돼 항목이 통째로 사라지는 버그가 실제로 있었습니다.
 *
 * <p>같은 트랜잭션에 둬도 중간에 명시적으로 {@code flush()}가 필요합니다 — Hibernate는 flush
 * 시점에 대기 중인 변경들을 INSERT → DELETE 순으로 내보내서(호출 순서가 아니라 Hibernate 내부
 * 우선순위), 삭제를 flush하지 않고 바로 저장하면 아직 DB에 남아있는 옛 행과 유니크 제약이
 * 충돌합니다. 이것도 실제로 겪은 문제입니다.
 *
 * <p>트리밍(개수 초과분 삭제)도 이 안에서 해야 합니다 — INSERT를 호출자 트랜잭션이 아니라
 * 여기서 커밋하기 때문에, 호출자 쪽에서 그 뒤에 count를 다시 조회하면 MySQL REPEATABLE READ
 * 스냅샷이 호출자 트랜잭션 시작 시점 기준이라 방금 여기서 커밋한 INSERT를 못 보고 개수를
 * 실제보다 하나 적게 셉니다. 같은 트랜잭션 안에서는 자기 자신이 쓴 것을 바로 볼 수 있어서
 * 여기로 옮겼습니다.
 *
 * <p>호출자와 분리한 이유는 {@code PopupLikeWriter}와 같습니다 — 유니크 제약 위반을 호출자
 * 트랜잭션에서 그대로 잡으면 rollback-only 상태라 커밋 시점에 {@code UnexpectedRollbackException}
 * 이 납니다.
 *
 * <p><b>별도 빈인 이유:</b> 같은 빈 안에서 호출하면 프록시를 타지 않아
 * {@code REQUIRES_NEW} 가 적용되지 않습니다.
 *
 * <p>엔티티가 아니라 id를 받아 {@link UserRepository#getReferenceById}로 다시 참조를 얻습니다.
 * 호출자의 트랜잭션(다른 영속성 컨텍스트)에서 이미 로드한 엔티티를 그대로 넘기면, 이 메서드의
 * REQUIRES_NEW 세션 기준으로는 detached 상태라 {@code PopupLikeWriter}와 같은 이유로 피합니다.
 */
@Component
@RequiredArgsConstructor
class UserSearchHistoryWriter {

    private final UserSearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replace(Long searcherId, Long targetUserId, int maxHistorySize) {
        searchHistoryRepository.deleteBySearcherIdAndSearchedUserId(searcherId, targetUserId);
        searchHistoryRepository.flush();

        User searcher = userRepository.getReferenceById(searcherId);
        User target = userRepository.getReferenceById(targetUserId);
        searchHistoryRepository.saveAndFlush(UserSearchHistory.of(searcher, target));

        while (searchHistoryRepository.countBySearcherId(searcherId) > maxHistorySize) {
            searchHistoryRepository.findFirstBySearcherIdOrderByIdAsc(searcherId)
                    .ifPresent(searchHistoryRepository::delete);
        }
    }
}
