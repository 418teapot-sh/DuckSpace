package com.duckspace.domain.user.service;

import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.entity.UserSearchHistory;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.domain.user.repository.UserSearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 기존 항목 삭제 + INSERT + 최대 개수 트리밍을 <b>한 트랜잭션</b>에서, 그리고 호출자와는
 * <b>별도 트랜잭션</b>에서 수행합니다.
 *
 * <p>삭제와 INSERT를 반드시 같은 트랜잭션(같은 커넥션)에 둬야 합니다 — 분리하면 삭제가
 * 아직 커밋되지 않은 상태에서 INSERT가 실행돼(REQUIRES_NEW라 서로 다른 트랜잭션이 서로의
 * 미커밋 변경을 못 봄) 옛 행이 남아있는 것으로 보여 유니크 제약 위반이 나고, 그 사이 바깥
 * 트랜잭션이 커밋되면서 삭제만 반영돼 항목이 통째로 사라지는 버그가 실제로 있었습니다.
 *
 * <p>{@link UserSearchHistoryRepository}의 삭제 메서드들은 전부 {@code @Modifying} 벌크
 * 쿼리라 호출 즉시 SQL이 나갑니다 — 그래서 여기서 별도로 {@code flush()}를 부를 필요가
 * 없습니다(파생 {@code deleteBy...} 메서드였을 때는 삭제가 다음 flush까지 미뤄지고, 그 flush
 * 시점에 Hibernate가 INSERT를 DELETE보다 먼저 내보내서 유니크 제약과 충돌하는 문제가 있었는데,
 * 벌크 쿼리로 바꾸면서 이 문제 자체가 사라졌습니다).
 *
 * <p>트리밍(개수 초과분 삭제)도 이 안에서 해야 합니다 — INSERT를 호출자 트랜잭션이 아니라
 * 여기서 커밋하기 때문에, 호출자 쪽에서 그 뒤에 count를 다시 조회하면 MySQL REPEATABLE READ
 * 스냅샷이 호출자 트랜잭션 시작 시점 기준이라 방금 여기서 커밋한 INSERT를 못 보고 개수를
 * 실제보다 하나 적게 셉니다. 같은 트랜잭션 안에서는 자기 자신이 쓴 것을 바로 볼 수 있어서
 * 여기로 옮겼습니다.
 *
 * <p><b>트리밍이 벌크 삭제인 이유는 정확성 때문이지 성능 때문만이 아닙니다.</b> "count 확인 →
 * 가장 오래된 것 하나 삭제(엔티티 remove)"를 while로 반복하던 이전 버전은, 같은 searcher에
 * 서로 다른 target으로 동시에 두 번 record()가 호출되면 실제로 죽었습니다 — 두 트랜잭션이 같은
 * "가장 오래된 행"을 찾아 각자 지우려 하는데, 진 쪽의 엔티티 remove는 그 행이 이미 없다는 걸
 * 알아채고 {@code ObjectOptimisticLockingFailureException}을 던집니다({@code @Version}
 * 유무와 무관하게 Hibernate가 항상 삭제된 행 수를 확인하기 때문 — "행이 없으면 조용히 넘어간다"는
 * 가정이 틀렸습니다). 벌크 {@code @Modifying} 쿼리는 몇 건이 지워지든(0건이어도) 예외 없이
 * 끝나서 이 경합 자체가 안전합니다.
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

        User searcher = userRepository.getReferenceById(searcherId);
        User target = userRepository.getReferenceById(targetUserId);
        searchHistoryRepository.saveAndFlush(UserSearchHistory.of(searcher, target));

        List<Long> keepIds = searchHistoryRepository.findIdsBySearcherIdOrderByIdDesc(
                searcherId, PageRequest.of(0, maxHistorySize));
        searchHistoryRepository.deleteBySearcherIdAndIdNotIn(searcherId, keepIds);
    }

    /**
     * {@code replace}가 유니크 제약 위반으로 실패했을 때, 그게 동시 요청이 먼저 넣어둔 것인지
     * 재확인하는 용도입니다. {@code FollowWriter.existsByFollowerAndFollowing}와 같은 이유로
     * REQUIRES_NEW입니다 — 호출자 트랜잭션에서 그대로 재조회하면 MySQL REPEATABLE READ 스냅샷이
     * 호출자의 최초 조회 시점에 고정돼 있어서, 방금 다른 트랜잭션이 커밋한 행을 못 볼 수 있습니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean exists(Long searcherId, Long targetUserId) {
        return searchHistoryRepository.existsBySearcherIdAndSearchedUserId(searcherId, targetUserId);
    }
}
