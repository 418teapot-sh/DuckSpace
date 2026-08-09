package com.duckspace.domain.chat.service;

import com.duckspace.domain.chat.dto.response.ChatMessageResponse;
import com.duckspace.domain.chat.dto.response.ChatRoomResponse;
import com.duckspace.domain.chat.entity.ChatMessage;
import com.duckspace.domain.chat.entity.ChatRoom;
import com.duckspace.domain.chat.exception.ChatErrorCode;
import com.duckspace.domain.chat.repository.ChatMessageRepository;
import com.duckspace.domain.chat.repository.ChatRoomRepository;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    /** 한 번에 내려주는 메시지 최대 개수. 폴링 응답이 과도하게 커지지 않도록 상한을 둡니다. */
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ChatRoomRegistry chatRoomRegistry;

    /**
     * 상대방과의 채팅방을 가져오고, 없으면 만듭니다. 여러 번 호출해도 방은 하나만 생깁니다.
     *
     * <p>이미 대화가 오간 방이면 목록 조회와 동일하게 마지막 메시지·안 읽음 여부를 채워서 돌려줍니다.
     */
    public ChatRoomResponse createOrGetRoom(Long myId, Long partnerId) {
        if (Objects.equals(myId, partnerId)) {
            throw new BusinessException(ChatErrorCode.CANNOT_CHAT_WITH_SELF);
        }
        User partner = userRepository.findById(partnerId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.PARTNER_NOT_FOUND));

        ChatRoom room = findOrCreateRoom(myId, partnerId);
        ChatMessage lastMessage = chatMessageRepository.findTopByRoomIdOrderByIdDesc(room.getId()).orElse(null);

        return ChatRoomResponse.of(room, myId, partner.getNickname(), lastMessage);
    }

    /**
     * 내가 참여 중인 채팅방 목록. 마지막 메시지가 최근인 순서입니다.
     *
     * <p>방마다 마지막 메시지·닉네임을 따로 조회하면 N+1이 되므로, 각각 쿼리 한 번으로 모아서 가져옵니다.
     */
    public List<ChatRoomResponse> getMyRooms(Long myId) {
        List<ChatRoom> rooms = chatRoomRepository.findAllByParticipant(myId);
        if (rooms.isEmpty()) {
            return List.of();
        }

        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        Map<Long, ChatMessage> lastMessages = chatMessageRepository.findLastMessagesOfRooms(roomIds).stream()
                .collect(Collectors.toMap(message -> message.getRoom().getId(), Function.identity()));

        List<Long> partnerIds = rooms.stream().map(room -> room.partnerOf(myId)).toList();
        Map<Long, String> nicknames = userRepository.findAllById(partnerIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        return rooms.stream()
                .map(room -> ChatRoomResponse.of(
                        room, myId, nicknames.get(room.partnerOf(myId)), lastMessages.get(room.getId())))
                .sorted(Comparator.comparing(
                        ChatRoomResponse::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * 메시지 조회. 조회한 지점까지 읽음으로 표시합니다.
     *
     * @param afterId null 이면 최근 메시지부터(최초 진입), 값이 있으면 그 이후 메시지만(폴링)
     */
    @Transactional
    public List<ChatMessageResponse> getMessages(Long myId, Long roomId, Long afterId, Integer size) {
        ChatRoom room = getRoomAsParticipant(roomId, myId);
        Pageable pageable = PageRequest.of(0, normalizeSize(size));

        List<ChatMessage> messages;
        if (afterId == null) {
            // 리포지토리가 최신순으로 주므로 뒤집기만 하면 됩니다. (이미 정렬된 리스트라 재정렬 불필요)
            messages = new ArrayList<>(chatMessageRepository.findByRoomIdOrderByIdDesc(roomId, pageable));
            Collections.reverse(messages);
        } else {
            messages = chatMessageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(roomId, afterId, pageable);
        }

        if (!messages.isEmpty()) {
            markRead(room, myId, messages.get(messages.size() - 1).getId());
        }

        return messages.stream()
                .map(message -> ChatMessageResponse.of(message, myId))
                .toList();
    }

    @Transactional
    public ChatMessageResponse sendMessage(Long myId, Long roomId, String content) {
        ChatRoom room = getRoomAsParticipant(roomId, myId);
        ChatMessage saved = chatMessageRepository.save(new ChatMessage(room, myId, content));

        // 보낸 사람 입장에서는 이미 읽은 메시지입니다.
        markRead(room, myId, saved.getId());

        return ChatMessageResponse.of(saved, myId);
    }

    /** 읽음 위치가 뒤로 밀리지 않도록 원자적 UPDATE 로 처리합니다. */
    private void markRead(ChatRoom room, Long userId, Long messageId) {
        if (room.isUserA(userId)) {
            chatRoomRepository.markReadForUserA(room.getId(), messageId);
        } else {
            chatRoomRepository.markReadForUserB(room.getId(), messageId);
        }
    }

    private ChatRoom getRoomAsParticipant(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.ROOM_NOT_FOUND));
        if (!room.hasParticipant(userId)) {
            throw new BusinessException(ChatErrorCode.NOT_ROOM_PARTICIPANT);
        }
        return room;
    }

    /**
     * 두 사람이 동시에 채팅을 시작하면 양쪽 다 "방 없음"을 보고 INSERT 하므로 한쪽이 유니크 제약에 걸립니다.
     * 이때는 <b>실패한 트랜잭션을 버리고 새 트랜잭션으로 다시 시도</b>합니다.
     * 같은 트랜잭션에서 재조회하면 세션 오염과 스냅샷 문제로 복구되지 않습니다.
     * ({@link ChatRoomRegistry} 참고)
     */
    private ChatRoom findOrCreateRoom(Long myId, Long partnerId) {
        try {
            return chatRoomRegistry.findOrCreate(myId, partnerId);
        } catch (DataIntegrityViolationException e) {
            return chatRoomRegistry.findOrCreate(myId, partnerId);
        }
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
