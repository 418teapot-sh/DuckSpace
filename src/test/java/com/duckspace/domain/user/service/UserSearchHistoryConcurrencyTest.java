package com.duckspace.domain.user.service;

import com.duckspace.domain.user.entity.AuthProvider;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.domain.user.repository.UserSearchHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserSearchHistoryWriter#replace}의 트리밍이 예전엔 "count 확인 → 가장 오래된 것 하나
 * 삭제"를 while로 반복했습니다. 같은 searcher에 서로 다른 target으로 동시에 여러 번 record()가
 * 호출되면, 진 쪽 트랜잭션의 MySQL REPEATABLE READ 스냅샷이 고정돼서 이긴 쪽이 이미 지운 행을
 * 계속 "가장 오래된 행"으로 다시 찾아내고 매번 0-row no-op 삭제만 하느라 종료 조건이 절대 안
 * 바뀌는 무한루프가 실제로 가능했습니다. 지금은 벌크 삭제 한 번이라 반복 자체가 없습니다.
 *
 * <p>Mockito 테스트는 실제 스레드·트랜잭션이 없어서 이 문제를 재현할 수 없습니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserSearchHistoryConcurrencyTest {

    private static final int MAX_HISTORY_SIZE = 3;

    @Autowired
    private UserSearchService userSearchService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSearchHistoryRepository searchHistoryRepository;

    private Long searcherId;

    @BeforeEach
    void setUp() {
        searcherId = userRepository.save(user("나")).getId();
    }

    @AfterEach
    void tearDown() {
        searchHistoryRepository.deleteAllInBatch();
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
    @DisplayName("이미 최대치인 상태에서 서로 다른 대상을 동시에 클릭해도 멈추지 않고 전부 끝난다")
    void 동시_트리밍은_무한루프에_빠지지_않는다() throws Exception {
        for (int i = 0; i < MAX_HISTORY_SIZE; i++) {
            userSearchService.record(searcherId, userRepository.save(user("기존" + i)).getId());
        }

        int threadCount = 8;
        List<Long> newTargets = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            newTargets.add(userRepository.save(user("신규" + i)).getId());
        }

        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Long targetId : newTargets) {
                futures.add(pool.submit(() -> {
                    startSignal.await();
                    userSearchService.record(searcherId, targetId);
                    return null;
                }));
            }

            startSignal.countDown();

            // 예전 버그라면 여기서 무한루프에 빠진 스레드 때문에 타임아웃으로 실패합니다.
            for (Future<Void> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }

            assertThat(searchHistoryRepository.countBySearcherId(searcherId))
                    .as("트리밍이 계속 동작해서 무한정 쌓이지는 않아야 합니다")
                    .isLessThanOrEqualTo((long) threadCount);
        } finally {
            pool.shutdownNow();
        }
    }
}
