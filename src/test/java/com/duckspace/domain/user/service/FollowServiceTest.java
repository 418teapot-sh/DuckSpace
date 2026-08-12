package com.duckspace.domain.user.service;

import com.duckspace.domain.user.entity.AuthProvider;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.exception.UserErrorCode;
import com.duckspace.domain.user.repository.FollowRepository;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;
    @Mock
    private UserRepository userRepository;

    private FollowService followService;

    @BeforeEach
    void setUp() {
        followService = new FollowService(followRepository, userRepository);
    }

    private User user(Long id) {
        User user = User.builder()
                .email("user" + id + "@duckspace.com")
                .nickname("유저" + id)
                .authProvider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Nested
    @DisplayName("follow 메서드는")
    class Follow {

        @Test
        void 정상적으로_팔로우를_저장한다() {
            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));

            followService.follow(1L, 2L);

            verify(followRepository).save(any());
        }

        @Test
        void 자기_자신을_팔로우하면_예외() {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> followService.follow(1L, 1L));

            assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.SELF_FOLLOW_NOT_ALLOWED);
            verify(followRepository, never()).save(any());
        }

        @Test
        void 이미_팔로우한_상대면_예외() {
            given(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).willReturn(true);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> followService.follow(1L, 2L));

            assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.ALREADY_FOLLOWING);
            verify(followRepository, never()).save(any());
        }

        @Test
        void 대상_유저가_없으면_예외() {
            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(userRepository.findById(2L)).willReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> followService.follow(1L, 2L));

            assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
            verify(followRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("unfollow 메서드는")
    class Unfollow {

        @Test
        void 정상적으로_팔로우를_삭제한다() {
            given(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).willReturn(true);

            followService.unfollow(1L, 2L);

            verify(followRepository).deleteByFollowerIdAndFollowingId(1L, 2L);
        }

        @Test
        void 팔로우하고_있지_않으면_예외() {
            given(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).willReturn(false);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> followService.unfollow(1L, 2L));

            assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.NOT_FOLLOWING);
            verify(followRepository, never()).deleteByFollowerIdAndFollowingId(any(), any());
        }
    }

    @Nested
    @DisplayName("countFollowers/countFollowing 메서드는")
    class Count {

        @Test
        void 팔로워_수와_팔로잉_수를_각각_위임한다() {
            given(followRepository.countByFollowingId(1L)).willReturn(3L);
            given(followRepository.countByFollowerId(1L)).willReturn(5L);

            assertThat(followService.countFollowers(1L)).isEqualTo(3L);
            assertThat(followService.countFollowing(1L)).isEqualTo(5L);
        }
    }
}
