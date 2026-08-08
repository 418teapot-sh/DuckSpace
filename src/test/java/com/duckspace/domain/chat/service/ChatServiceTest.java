package com.duckspace.domain.chat.service;

import com.duckspace.domain.chat.dto.response.ChatMessageResponse;
import com.duckspace.domain.chat.dto.response.ChatRoomResponse;
import com.duckspace.domain.chat.entity.ChatMessage;
import com.duckspace.domain.chat.entity.ChatRoom;
import com.duckspace.domain.chat.exception.ChatErrorCode;
import com.duckspace.domain.chat.repository.ChatMessageRepository;
import com.duckspace.domain.chat.repository.ChatRoomRepository;
import com.duckspace.domain.user.entity.AuthProvider;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Long ME = 1L;
    private static final Long PARTNER = 2L;
    private static final Long STRANGER = 99L;
    private static final Long ROOM_ID = 10L;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChatService chatService;

    private ChatRoom room;

    @BeforeEach
    void setUp() {
        room = ChatRoom.between(ME, PARTNER);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
    }

    private User user(Long id, String nickname) {
        User user = User.builder()
                .email(nickname + "@duckspace.com")
                .nickname(nickname)
                .password("encoded")
                .authProvider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private ChatMessage message(Long id, Long senderId, String content) {
        ChatMessage message = new ChatMessage(room, senderId, content);
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }

    @Nested
    @DisplayName("createOrGetRoom 메서드는")
    class CreateOrGetRoom {

        @Test
        void 방이_없으면_새로_만든다() {
            given(userRepository.findById(PARTNER)).willReturn(Optional.of(user(PARTNER, "덕질왕")));
            given(chatRoomRepository.findByUserAIdAndUserBId(ME, PARTNER)).willReturn(Optional.empty());
            given(chatRoomRepository.saveAndFlush(any(ChatRoom.class))).willReturn(room);

            ChatRoomResponse response = chatService.createOrGetRoom(ME, PARTNER);

            assertThat(response.roomId()).isEqualTo(ROOM_ID);
            assertThat(response.partnerId()).isEqualTo(PARTNER);
            assertThat(response.partnerNickname()).isEqualTo("덕질왕");
            verify(chatRoomRepository).saveAndFlush(any(ChatRoom.class));
        }

        @Test
        void 이미_방이_있으면_새로_만들지_않는다() {
            given(userRepository.findById(PARTNER)).willReturn(Optional.of(user(PARTNER, "덕질왕")));
            given(chatRoomRepository.findByUserAIdAndUserBId(ME, PARTNER)).willReturn(Optional.of(room));

            ChatRoomResponse response = chatService.createOrGetRoom(ME, PARTNER);

            assertThat(response.roomId()).isEqualTo(ROOM_ID);
            verify(chatRoomRepository, never()).saveAndFlush(any(ChatRoom.class));
        }

        @Test
        void 요청_순서가_반대여도_같은_방을_찾는다() {
            given(userRepository.findById(ME)).willReturn(Optional.of(user(ME, "나")));
            given(chatRoomRepository.findByUserAIdAndUserBId(ME, PARTNER)).willReturn(Optional.of(room));

            ChatRoomResponse response = chatService.createOrGetRoom(PARTNER, ME);

            assertThat(response.roomId()).isEqualTo(ROOM_ID);
            assertThat(response.partnerId()).isEqualTo(ME);
        }

        @Test
        void 자기_자신과는_채팅할_수_없다() {
            BusinessException exception =
                    assertThrows(BusinessException.class, () -> chatService.createOrGetRoom(ME, ME));

            assertThat(exception.getErrorCode()).isEqualTo(ChatErrorCode.CANNOT_CHAT_WITH_SELF);
            verify(userRepository, never()).findById(anyLong());
        }

        @Test
        void 존재하지_않는_상대면_예외() {
            given(userRepository.findById(PARTNER)).willReturn(Optional.empty());

            BusinessException exception =
                    assertThrows(BusinessException.class, () -> chatService.createOrGetRoom(ME, PARTNER));

            assertThat(exception.getErrorCode()).isEqualTo(ChatErrorCode.PARTNER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getMessages 메서드는")
    class GetMessages {

        @Test
        void after가_없으면_최근_메시지를_오래된_순으로_돌려준다() {
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            // 리포지토리는 최신순으로 주고, 서비스가 뒤집어서 내려줘야 합니다.
            given(chatMessageRepository.findByRoomIdOrderByIdDesc(eq(ROOM_ID), any(Pageable.class)))
                    .willReturn(List.of(message(3L, PARTNER, "셋"), message(2L, ME, "둘"), message(1L, PARTNER, "하나")));

            List<ChatMessageResponse> messages = chatService.getMessages(ME, ROOM_ID, null, null);

            assertThat(messages).extracting(ChatMessageResponse::messageId).containsExactly(1L, 2L, 3L);
            assertThat(messages).extracting(ChatMessageResponse::content).containsExactly("하나", "둘", "셋");
        }

        @Test
        void after가_있으면_그_이후_메시지만_조회한다() {
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(chatMessageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(eq(ROOM_ID), eq(2L), any(Pageable.class)))
                    .willReturn(List.of(message(3L, PARTNER, "셋")));

            List<ChatMessageResponse> messages = chatService.getMessages(ME, ROOM_ID, 2L, null);

            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).messageId()).isEqualTo(3L);
            verify(chatMessageRepository, never()).findByRoomIdOrderByIdDesc(anyLong(), any(Pageable.class));
        }

        @Test
        void 내가_보낸_메시지는_mine이_true다() {
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(chatMessageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(eq(ROOM_ID), eq(0L), any(Pageable.class)))
                    .willReturn(List.of(message(1L, ME, "내 메시지"), message(2L, PARTNER, "상대 메시지")));

            List<ChatMessageResponse> messages = chatService.getMessages(ME, ROOM_ID, 0L, null);

            assertThat(messages).extracting(ChatMessageResponse::mine).containsExactly(true, false);
        }

        @Test
        void 조회하면_마지막_메시지까지_읽음_처리된다() {
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(chatMessageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(eq(ROOM_ID), eq(0L), any(Pageable.class)))
                    .willReturn(List.of(message(1L, PARTNER, "하나"), message(5L, PARTNER, "다섯")));

            chatService.getMessages(ME, ROOM_ID, 0L, null);

            assertThat(room.lastReadMessageIdOf(ME)).isEqualTo(5L);
        }

        @Test
        void 참여자가_아니면_예외() {
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> chatService.getMessages(STRANGER, ROOM_ID, null, null));

            assertThat(exception.getErrorCode()).isEqualTo(ChatErrorCode.NOT_ROOM_PARTICIPANT);
        }

        @Test
        void 없는_방이면_예외() {
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> chatService.getMessages(ME, ROOM_ID, null, null));

            assertThat(exception.getErrorCode()).isEqualTo(ChatErrorCode.ROOM_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("sendMessage 메서드는")
    class SendMessage {

        @Test
        void 메시지를_저장하고_돌려준다() {
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(message(7L, ME, "안녕하세요"));

            ChatMessageResponse response = chatService.sendMessage(ME, ROOM_ID, "안녕하세요");

            assertThat(response.messageId()).isEqualTo(7L);
            assertThat(response.content()).isEqualTo("안녕하세요");
            assertThat(response.mine()).isTrue();
        }

        @Test
        void 보낸_메시지는_보낸_사람_기준으로_읽음_처리된다() {
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(message(7L, ME, "안녕하세요"));

            chatService.sendMessage(ME, ROOM_ID, "안녕하세요");

            assertThat(room.lastReadMessageIdOf(ME)).isEqualTo(7L);
            assertThat(room.lastReadMessageIdOf(PARTNER)).isNull();
        }

        @Test
        void 참여자가_아니면_예외() {
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> chatService.sendMessage(STRANGER, ROOM_ID, "끼어들기"));

            assertThat(exception.getErrorCode()).isEqualTo(ChatErrorCode.NOT_ROOM_PARTICIPANT);
            verify(chatMessageRepository, never()).save(any(ChatMessage.class));
        }
    }

    @Nested
    @DisplayName("getMyRooms 메서드는")
    class GetMyRooms {

        @Test
        void 참여한_방이_없으면_빈_목록() {
            given(chatRoomRepository.findAllByParticipant(ME)).willReturn(List.of());

            assertThat(chatService.getMyRooms(ME)).isEmpty();
            verify(chatMessageRepository, never()).findLastMessagesOfRooms(any());
        }

        @Test
        void 상대_닉네임과_마지막_메시지를_채워서_돌려준다() {
            given(chatRoomRepository.findAllByParticipant(ME)).willReturn(List.of(room));
            given(chatMessageRepository.findLastMessagesOfRooms(List.of(ROOM_ID)))
                    .willReturn(List.of(message(4L, PARTNER, "내일 3시 어떠세요?")));
            given(userRepository.findAllById(List.of(PARTNER))).willReturn(List.of(user(PARTNER, "덕질왕")));

            List<ChatRoomResponse> rooms = chatService.getMyRooms(ME);

            assertThat(rooms).hasSize(1);
            assertThat(rooms.get(0).partnerNickname()).isEqualTo("덕질왕");
            assertThat(rooms.get(0).lastMessage()).isEqualTo("내일 3시 어떠세요?");
        }

        @Test
        void 상대가_보낸_안_읽은_메시지가_있으면_hasUnread가_true다() {
            given(chatRoomRepository.findAllByParticipant(ME)).willReturn(List.of(room));
            given(chatMessageRepository.findLastMessagesOfRooms(List.of(ROOM_ID)))
                    .willReturn(List.of(message(4L, PARTNER, "안 읽은 메시지")));
            given(userRepository.findAllById(List.of(PARTNER))).willReturn(List.of(user(PARTNER, "덕질왕")));

            assertThat(chatService.getMyRooms(ME).get(0).hasUnread()).isTrue();
        }

        @Test
        void 읽은_뒤에는_hasUnread가_false다() {
            room.markRead(ME, 4L);
            given(chatRoomRepository.findAllByParticipant(ME)).willReturn(List.of(room));
            given(chatMessageRepository.findLastMessagesOfRooms(List.of(ROOM_ID)))
                    .willReturn(List.of(message(4L, PARTNER, "읽은 메시지")));
            given(userRepository.findAllById(List.of(PARTNER))).willReturn(List.of(user(PARTNER, "덕질왕")));

            assertThat(chatService.getMyRooms(ME).get(0).hasUnread()).isFalse();
        }

        @Test
        void 내가_보낸_메시지가_마지막이면_hasUnread가_false다() {
            given(chatRoomRepository.findAllByParticipant(ME)).willReturn(List.of(room));
            given(chatMessageRepository.findLastMessagesOfRooms(List.of(ROOM_ID)))
                    .willReturn(List.of(message(4L, ME, "내가 보낸 메시지")));
            given(userRepository.findAllById(List.of(PARTNER))).willReturn(List.of(user(PARTNER, "덕질왕")));

            assertThat(chatService.getMyRooms(ME).get(0).hasUnread()).isFalse();
        }
    }
}
