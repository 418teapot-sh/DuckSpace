package com.duckspace.domain.chat.repository;

import com.duckspace.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /** 인자는 반드시 정렬된 값(작은 id, 큰 id)이어야 합니다. {@link ChatRoom#between} 참고. */
    Optional<ChatRoom> findByUserAIdAndUserBId(Long userAId, Long userBId);

    @Query("select r from ChatRoom r where r.userAId = :userId or r.userBId = :userId")
    List<ChatRoom> findAllByParticipant(@Param("userId") Long userId);
}
