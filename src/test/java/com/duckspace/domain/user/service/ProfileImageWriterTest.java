package com.duckspace.domain.user.service;

import com.duckspace.domain.exhibition.image.ImageCleanup;
import com.duckspace.domain.user.entity.AuthProvider;
import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.exception.UserErrorCode;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProfileImageWriterTest {

    private static final Long USER_ID = 10L;

    @Mock
    private ImageCleanup imageCleanup;

    @Mock
    private UserRepository userRepository;

    private ProfileImageWriter profileImageWriter;

    @BeforeEach
    void setUp() {
        profileImageWriter = new ProfileImageWriter(imageCleanup, userRepository);
    }

    private User newUser() {
        return User.builder()
                .email("mock@duckspace.com")
                .nickname("mock")
                .password("password12")
                .authProvider(AuthProvider.LOCAL)
                .build();
    }

    @Test
    void 최초_업로드면_프로필에_반영하고_정리할_이전_이미지가_없다() {
        User user = newUser();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        profileImageWriter.apply(USER_ID, "https://cdn/users/10/new.png");

        assertThat(user.getProfileImageUrl()).isEqualTo("https://cdn/users/10/new.png");
        // ImageCleanup.deleteAfterCommit(null)은 내부에서 아무것도 안 하지만,
        // "이전 URL이 없을 때도 호출 자체는 안전하게 통과한다"는 걸 명시적으로 남겨둡니다.
        verify(imageCleanup).deleteAfterCommit((String) null);
    }

    @Test
    void 기존_사진이_있으면_교체하고_이전_이미지를_정리한다() {
        User user = newUser();
        user.replaceProfileImage("https://cdn/users/10/old.png");
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        profileImageWriter.apply(USER_ID, "https://cdn/users/10/new.png");

        assertThat(user.getProfileImageUrl()).isEqualTo("https://cdn/users/10/new.png");
        verify(imageCleanup).deleteAfterCommit("https://cdn/users/10/old.png");
    }

    @Test
    void 존재하지_않는_유저면_거부한다() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> profileImageWriter.apply(USER_ID, "https://cdn/users/10/new.png"));

        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(imageCleanup);
    }
}
