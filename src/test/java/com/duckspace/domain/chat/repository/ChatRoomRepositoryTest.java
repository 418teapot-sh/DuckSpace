package com.duckspace.domain.chat.repository;

import com.duckspace.domain.chat.entity.ChatRoom;
import com.duckspace.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 읽음 위치 갱신은 "전진하는 경우에만 반영"되어야 합니다.
 * 조건부 UPDATE 라 목(mock) 으로는 검증되지 않으므로 실제 DB 로 확인합니다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class ChatRoomRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    private ChatRoom persistRoom(Long userA, Long userB) {
        ChatRoom room = entityManager.persist(ChatRoom.between(userA, userB));
        entityManager.flush();
        return room;
    }

    private ChatRoom reload(Long roomId) {
        entityManager.clear();
        return chatRoomRepository.findById(roomId).orElseThrow();
    }

    @Test
    @DisplayName("읽음 위치를 앞으로 전진시킨다")
    void 읽음_위치를_전진시킨다() {
        ChatRoom room = persistRoom(1L, 2L);

        chatRoomRepository.markReadForUserA(room.getId(), 5L);

        assertThat(reload(room.getId()).lastReadMessageIdOf(1L)).isEqualTo(5L);
    }

    @Test
    @DisplayName("이미 더 뒤까지 읽었으면 뒤로 되돌리지 않는다")
    void 읽음_위치가_뒤로_가지_않는다() {
        ChatRoom room = persistRoom(1L, 2L);
        chatRoomRepository.markReadForUserA(room.getId(), 10L);

        // 늦게 도착한 오래된 갱신 (동시 폴링 상황)
        chatRoomRepository.markReadForUserA(room.getId(), 3L);

        assertThat(reload(room.getId()).lastReadMessageIdOf(1L)).isEqualTo(10L);
    }

    @Test
    @DisplayName("한쪽 참여자의 읽음 위치만 갱신된다")
    void 상대방_읽음_위치는_건드리지_않는다() {
        ChatRoom room = persistRoom(1L, 2L);

        chatRoomRepository.markReadForUserA(room.getId(), 5L);

        ChatRoom reloaded = reload(room.getId());
        assertThat(reloaded.lastReadMessageIdOf(1L)).isEqualTo(5L);
        assertThat(reloaded.lastReadMessageIdOf(2L)).isNull();
    }

    @Test
    @DisplayName("두 참여자 중 누구로 조회해도 방 목록에 나온다")
    void 양쪽_참여자_모두_방_목록에서_조회된다() {
        ChatRoom room = persistRoom(1L, 2L);
        entityManager.clear();

        assertThat(chatRoomRepository.findAllByParticipant(1L)).extracting(ChatRoom::getId).containsExactly(room.getId());
        assertThat(chatRoomRepository.findAllByParticipant(2L)).extracting(ChatRoom::getId).containsExactly(room.getId());
        assertThat(chatRoomRepository.findAllByParticipant(3L)).isEmpty();
    }
}
