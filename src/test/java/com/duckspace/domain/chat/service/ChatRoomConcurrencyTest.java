package com.duckspace.domain.chat.service;

import com.duckspace.domain.chat.entity.ChatRoom;
import com.duckspace.domain.chat.repository.ChatMessageRepository;
import com.duckspace.domain.chat.repository.ChatRoomRepository;
import com.duckspace.domain.user.entity.AuthProvider;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 채팅방 동시 생성 복구가 <b>실제로</b> 동작하는지 검증합니다.
 *
 * <p>목(mock) 테스트로는 "예외가 나면 한 번 더 호출한다"는 제어 흐름만 확인됩니다.
 * 정작 중요한 건 <b>제약 위반이 난 뒤에도 다음 호출이 살아있는가</b>인데,
 * 그건 실제 Hibernate 세션이 있어야 드러납니다.
 * (같은 트랜잭션에서 재조회하면 {@code AssertionFailure: ... has a null identifier} 로 죽습니다.)
 *
 * <p>그래서 트랜잭션이 실제로 커밋되도록 {@code @Transactional} 없이 실행합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ChatRoomConcurrencyTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatRoomRegistry chatRoomRegistry;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserRepository userRepository;

    private Long meId;
    private Long partnerId;

    @BeforeEach
    void setUp() {
        meId = userRepository.save(user("me")).getId();
        partnerId = userRepository.save(user("partner")).getId();
    }

    @AfterEach
    void tearDown() {
        // 트랜잭션 롤백이 없으므로 직접 정리합니다.
        chatMessageRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private User user(String nickname) {
        return User.builder()
                .email(nickname + "-" + System.nanoTime() + "@duckspace.com")
                .nickname(nickname)
                .password("encoded")
                .authProvider(AuthProvider.LOCAL)
                .build();
    }

    @Test
    @DisplayName("유니크 위반이 난 뒤에도 다음 호출이 정상 동작한다 (세션이 오염되지 않는다)")
    void 제약위반_이후에도_다음_호출이_살아있다() {
        ChatRoom existing = chatRoomRegistry.findOrCreate(meId, partnerId);

        // 상대가 먼저 만든 상황을 재현: 같은 참여자 조합으로 강제 INSERT
        assertThrows(DataIntegrityViolationException.class,
                () -> chatRoomRepository.saveAndFlush(ChatRoom.between(meId, partnerId)));

        // 핵심: 위반 직후에도 조회가 되어야 한다. 같은 트랜잭션이었다면 여기서 AssertionFailure 가 난다.
        ChatRoom recovered = chatRoomRegistry.findOrCreate(meId, partnerId);

        assertThat(recovered.getId()).isEqualTo(existing.getId());
        assertThat(chatRoomRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 요청이 동시에 들어와도 방은 하나만 생기고 모두 같은 방을 받는다")
    void 동시_요청에도_방은_하나만_생긴다() throws Exception {
        int threadCount = 8;
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    startSignal.await();
                    return chatService.createOrGetRoom(meId, partnerId).roomId();
                }));
            }

            startSignal.countDown();   // 동시에 출발

            Set<Long> roomIds = new HashSet<>();
            for (Future<Long> future : futures) {
                roomIds.add(future.get(20, TimeUnit.SECONDS));
            }

            assertThat(roomIds)
                    .as("모든 요청이 같은 방 id 를 받아야 합니다")
                    .hasSize(1);
            assertThat(chatRoomRepository.count())
                    .as("방이 중복 생성되면 안 됩니다")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
