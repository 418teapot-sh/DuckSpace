package com.duckspace.domain.user.service;

import com.duckspace.domain.user.entity.AuthProvider;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.repository.FollowRepository;
import com.duckspace.domain.user.repository.UserRepository;
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
 * follow() 가 동시 요청에서도 실제로 멱등한지 검증합니다.
 *
 * <p>Mockito 목 테스트는 유니크 제약 위반 이후의 재확인 쿼리가 "다르게" 응답하도록 순서대로
 * 스텁할 뿐입니다. 실제로는 그 재확인이 같은 트랜잭션 안에서 이뤄지면, MySQL REPEATABLE READ의
 * 스냅샷 때문에 다른 트랜잭션이 방금 커밋한 row를 못 볼 수 있습니다 — 이 문제는 실제 Hibernate
 * 세션과 트랜잭션 경계가 있어야 드러나서, 목 테스트로는 잡을 수 없습니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class FollowConcurrencyTest {

    @Autowired
    private FollowService followService;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private UserRepository userRepository;

    private Long followerId;
    private Long followingId;

    @BeforeEach
    void setUp() {
        followerId = userRepository.save(user("follower")).getId();
        followingId = userRepository.save(user("following")).getId();
    }

    @AfterEach
    void tearDown() {
        followRepository.deleteAllInBatch();
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
    @DisplayName("동시에 여러 번 팔로우해도 전부 성공하고 기록은 하나만 남는다")
    void 동시_팔로우_요청은_전부_성공한다() throws Exception {
        int threadCount = 8;
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    startSignal.await();
                    followService.follow(followerId, followingId);
                    return null;
                }));
            }

            startSignal.countDown();   // 동시에 출발

            // 재확인이 옛 스냅샷을 보면 여기서 BusinessException(USER_NOT_FOUND)이 던져집니다.
            for (Future<Void> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }

            assertThat(followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)).isTrue();
            assertThat(followRepository.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
