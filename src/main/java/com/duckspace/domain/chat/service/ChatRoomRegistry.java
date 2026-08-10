package com.duckspace.domain.chat.service;

import com.duckspace.domain.chat.entity.ChatRoom;
import com.duckspace.domain.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅방 조회/생성만 담당합니다. <b>매번 독립된 트랜잭션</b>에서 실행하기 위해 별도 빈으로 분리했습니다.
 *
 * <p>같은 트랜잭션 안에서 "INSERT 실패 → 재조회"를 하면 두 가지 이유로 동작하지 않습니다.
 * <ol>
 *   <li>제약 위반이 나면 Hibernate 가 세션을 오염된 상태로 표시해서, 이어지는 조회가
 *       {@code AssertionFailure: ... has a null identifier} 로 죽습니다.</li>
 *   <li>MySQL 기본 격리수준(REPEATABLE READ)에서는 재조회가 최초 SELECT 시점 스냅샷을 그대로 보므로,
 *       상대가 방금 커밋한 방이 여전히 보이지 않습니다.</li>
 * </ol>
 *
 * <p>그래서 실패한 시도는 이 안쪽 트랜잭션과 함께 통째로 버리고,
 * 호출부가 <b>새 트랜잭션으로 다시 호출</b>하도록 합니다. 새 트랜잭션은 스냅샷도 세션도 새로 잡습니다.
 */
@Component
@RequiredArgsConstructor
class ChatRoomRegistry {

    private final ChatRoomRepository chatRoomRepository;

    /**
     * 방을 조회하고 없으면 만듭니다. 유니크 제약 위반은 잡지 않고 그대로 던집니다.
     * (상대가 먼저 만든 경우이므로, 호출부가 다시 호출하면 조회로 해결됩니다.)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatRoom findOrCreate(Long myId, Long partnerId) {
        return chatRoomRepository
                .findByUserAIdAndUserBId(ChatRoom.smallerId(myId, partnerId), ChatRoom.largerId(myId, partnerId))
                // IDENTITY 전략이라 save() 만으로도 즉시 INSERT 되지만,
                // 유니크 위반을 이 트랜잭션 안에서 받는다는 의도를 드러내기 위해 saveAndFlush 를 씁니다.
                .orElseGet(() -> chatRoomRepository.saveAndFlush(ChatRoom.between(myId, partnerId)));
    }
}
