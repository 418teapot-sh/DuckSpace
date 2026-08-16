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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FollowWriter followWriter;

    private FollowService followService;

    @BeforeEach
    void setUp() {
        followService = new FollowService(followRepository, userRepository, followWriter);
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

            verify(followWriter).insert(any(), any());
        }

        @Test
        void 동시_요청으로_DB_유니크_제약조건이_위반되면_성공으로_처리한다() {
            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
            given(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).willReturn(false);
            given(followWriter.existsByFollowerAndFollowing(1L, 2L)).willReturn(true);
            doThrow(new DataIntegrityViolationException("duplicate")).when(followWriter).insert(any(), any());

            followService.follow(1L, 2L);

            verify(followWriter).insert(any(), any());
        }

        @Test
        void FK_제약조건_위반이면_예외() {
            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
            given(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).willReturn(false);
            given(followWriter.existsByFollowerAndFollowing(1L, 2L)).willReturn(false);
            doThrow(new DataIntegrityViolationException("FK violation")).when(followWriter).insert(any(), any());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> followService.follow(1L, 2L));

            assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }

        @Test
        void 자기_자신을_팔로우하면_예외() {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> followService.follow(1L, 1L));

            assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.SELF_FOLLOW_NOT_ALLOWED);
            verify(followWriter, never()).insert(any(), any());
        }

        @Test
        void 이미_팔로우한_상대면_아무_일도_하지_않는다() {
            given(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).willReturn(true);

            followService.follow(1L, 2L);

            verify(followWriter, never()).insert(any(), any());
        }

        @Test
        void 대상_유저가_없으면_예외() {
            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(userRepository.findById(2L)).willReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> followService.follow(1L, 2L));

            assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
            verify(followWriter, never()).insert(any(), any());
        }
    }

    @Nested
    @DisplayName("unfollow 메서드는")
    class Unfollow {

        @Test
        void 팔로우_관계_존재_여부와_무관하게_삭제를_위임한다() {
            followService.unfollow(1L, 2L);

            verify(followRepository).deleteByFollowerIdAndFollowingId(1L, 2L);
        }
    }

    @Nested
    @DisplayName("getFollowers 메서드는")
    class GetFollowers {

        @Test
        void 존재하지_않는_유저면_예외() {
            given(userRepository.existsById(1L)).willReturn(false);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> followService.getFollowers(1L, null, null));

            assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
            verify(followRepository, never()).findFollowersByFollowingId(any(), any(), any());
        }

        @Test
        void 존재하는_유저면_팔로워_목록을_조회한다() {
            given(userRepository.existsById(1L)).willReturn(true);
            given(followRepository.findFollowersByFollowingId(any(), any(), any())).willReturn(List.of());

            assertThat(followService.getFollowers(1L, null, null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getFollowing 메서드는")
    class GetFollowing {

        @Test
        void 존재하지_않는_유저면_예외() {
            given(userRepository.existsById(1L)).willReturn(false);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> followService.getFollowing(1L, null, null));

            assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
            verify(followRepository, never()).findFollowingByFollowerId(any(), any(), any());
        }

        @Test
        void 존재하는_유저면_팔로잉_목록을_조회한다() {
            given(userRepository.existsById(1L)).willReturn(true);
            given(followRepository.findFollowingByFollowerId(any(), any(), any())).willReturn(List.of());

            assertThat(followService.getFollowing(1L, null, null)).isEmpty();
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
