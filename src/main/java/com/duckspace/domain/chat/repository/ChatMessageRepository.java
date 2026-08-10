package com.duckspace.domain.chat.repository;

import com.duckspace.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 폴링용. {@code afterId} 이후에 생긴 메시지를 오래된 순으로 반환합니다. */
    List<ChatMessage> findByRoomIdAndIdGreaterThanOrderByIdAsc(Long roomId, Long afterId, Pageable pageable);

    /** 최초 진입용. 최신 메시지부터 가져오므로 응답 전에 뒤집어야 합니다. */
    List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    /** 방 하나의 마지막 메시지. 채팅방 조회/생성 응답을 목록과 같은 내용으로 채우는 데 씁니다. */
    Optional<ChatMessage> findTopByRoomIdOrderByIdDesc(Long roomId);

    /**
     * 여러 방의 마지막 메시지를 한 번에 조회합니다.
     * 채팅방 목록에서 방마다 따로 조회하면 N+1이 되므로 쿼리 하나로 가져옵니다.
     */
    @Query("""
            select m from ChatMessage m
            where m.id in (
                select max(m2.id) from ChatMessage m2
                where m2.room.id in :roomIds
                group by m2.room.id
            )
            """)
    List<ChatMessage> findLastMessagesOfRooms(@Param("roomIds") Collection<Long> roomIds);
}
