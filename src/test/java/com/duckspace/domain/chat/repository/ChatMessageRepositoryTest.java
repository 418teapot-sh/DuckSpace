package com.duckspace.domain.chat.repository;

import com.duckspace.domain.chat.entity.ChatMessage;
import com.duckspace.domain.chat.entity.ChatRoom;
import com.duckspace.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 방별 마지막 메시지 조회는 JPQL 서브쿼리라 목(mock) 테스트로는 검증되지 않습니다.
 * 실제 DB에 붙여서 확인합니다.
 *
 * <p>{@code createdAt} 이 not null 이므로 Auditing 설정을 함께 올려야 합니다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class ChatMessageRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private ChatRoom persistRoom(Long userA, Long userB) {
        return entityManager.persist(ChatRoom.between(userA, userB));
    }

    private ChatMessage persistMessage(ChatRoom room, Long senderId, String content) {
        return entityManager.persist(new ChatMessage(room, senderId, content));
    }

    @Test
    @DisplayName("방마다 가장 마지막 메시지 하나씩만 가져온다")
    void 방별_마지막_메시지를_한_번에_가져온다() {
        ChatRoom roomA = persistRoom(1L, 2L);
        ChatRoom roomB = persistRoom(1L, 3L);

        persistMessage(roomA, 1L, "A-첫번째");
        persistMessage(roomA, 2L, "A-두번째");
        ChatMessage lastOfA = persistMessage(roomA, 1L, "A-마지막");

        persistMessage(roomB, 3L, "B-첫번째");
        ChatMessage lastOfB = persistMessage(roomB, 1L, "B-마지막");
        entityManager.flush();

        List<ChatMessage> lastMessages =
                chatMessageRepository.findLastMessagesOfRooms(List.of(roomA.getId(), roomB.getId()));

        assertThat(lastMessages)
                .hasSize(2)
                .extracting(ChatMessage::getId)
                .containsExactlyInAnyOrder(lastOfA.getId(), lastOfB.getId());
    }

    @Test
    @DisplayName("메시지가 없는 방은 결과에 포함되지 않는다")
    void 메시지가_없는_방은_제외된다() {
        ChatRoom empty = persistRoom(1L, 2L);
        entityManager.flush();

        assertThat(chatMessageRepository.findLastMessagesOfRooms(List.of(empty.getId()))).isEmpty();
    }

    @Test
    @DisplayName("폴링 조회는 커서 이후 메시지만 오래된 순으로 가져온다")
    void 커서_이후_메시지만_오름차순으로_가져온다() {
        ChatRoom room = persistRoom(1L, 2L);
        ChatMessage first = persistMessage(room, 1L, "하나");
        ChatMessage second = persistMessage(room, 2L, "둘");
        ChatMessage third = persistMessage(room, 1L, "셋");
        entityManager.flush();

        List<ChatMessage> messages = chatMessageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(
                room.getId(), first.getId(), PageRequest.of(0, 50));

        assertThat(messages)
                .extracting(ChatMessage::getId)
                .containsExactly(second.getId(), third.getId());
    }

    @Test
    @DisplayName("최초 진입 조회는 최신 메시지부터 가져온다")
    void 최초_진입은_최신순으로_가져온다() {
        ChatRoom room = persistRoom(1L, 2L);
        persistMessage(room, 1L, "하나");
        ChatMessage second = persistMessage(room, 2L, "둘");
        ChatMessage third = persistMessage(room, 1L, "셋");
        entityManager.flush();

        List<ChatMessage> messages =
                chatMessageRepository.findByRoomIdOrderByIdDesc(room.getId(), PageRequest.of(0, 2));

        assertThat(messages)
                .extracting(ChatMessage::getId)
                .containsExactly(third.getId(), second.getId());
    }
}
