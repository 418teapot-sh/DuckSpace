package com.duckspace.domain.chat.repository;

import com.duckspace.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /** 인자는 반드시 정렬된 값이어야 합니다. {@link ChatRoom#smallerId} / {@link ChatRoom#largerId} 참고. */
    Optional<ChatRoom> findByUserAIdAndUserBId(Long userAId, Long userBId);

    @Query("select r from ChatRoom r where r.userAId = :userId or r.userBId = :userId")
    List<ChatRoom> findAllByParticipant(@Param("userId") Long userId);

    /**
     * 읽음 위치를 원자적으로 전진시킵니다.
     *
     * <p>엔티티를 읽어서 수정하면, 같은 사용자가 탭 두 개로 동시에 폴링할 때
     * 나중에 커밋되는 쪽이 앞선 갱신을 덮어써 읽음 위치가 <b>과거로 되돌아갈</b> 수 있습니다.
     * {@code where} 조건으로 전진하는 경우에만 갱신되게 해서 그 역전을 막습니다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ChatRoom r set r.userALastReadMessageId = :messageId
            where r.id = :roomId
              and (r.userALastReadMessageId is null or r.userALastReadMessageId < :messageId)
            """)
    void markReadForUserA(@Param("roomId") Long roomId, @Param("messageId") Long messageId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ChatRoom r set r.userBLastReadMessageId = :messageId
            where r.id = :roomId
              and (r.userBLastReadMessageId is null or r.userBLastReadMessageId < :messageId)
            """)
    void markReadForUserB(@Param("roomId") Long roomId, @Param("messageId") Long messageId);
}
