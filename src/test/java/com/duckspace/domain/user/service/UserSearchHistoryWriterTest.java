package com.duckspace.domain.user.service;

import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.entity.UserSearchHistory;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.domain.user.repository.UserSearchHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserSearchHistoryWriterTest {

    private static final Long SEARCHER_ID = 1L;
    private static final Long TARGET_ID = 2L;
    private static final int MAX_HISTORY_SIZE = 3;

    @Mock
    private UserSearchHistoryRepository searchHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserSearchHistoryWriter writer;

    @Test
    @DisplayName("삭제 후 flush하고 나서 새로 저장한다")
    void 삭제_flush_저장_순서() {
        given(userRepository.getReferenceById(SEARCHER_ID)).willReturn(mock(User.class));
        given(userRepository.getReferenceById(TARGET_ID)).willReturn(mock(User.class));
        given(searchHistoryRepository.countBySearcherId(SEARCHER_ID)).willReturn(1L);

        writer.replace(SEARCHER_ID, TARGET_ID, MAX_HISTORY_SIZE);

        verify(searchHistoryRepository).deleteBySearcherIdAndSearchedUserId(SEARCHER_ID, TARGET_ID);
        verify(searchHistoryRepository).flush();
        verify(searchHistoryRepository).saveAndFlush(any(UserSearchHistory.class));
    }

    @Test
    @DisplayName("최대 개수를 넘으면 기준 이하가 될 때까지 가장 오래된 것부터 지운다")
    void 초과분_반복_삭제() {
        given(userRepository.getReferenceById(SEARCHER_ID)).willReturn(mock(User.class));
        given(userRepository.getReferenceById(TARGET_ID)).willReturn(mock(User.class));
        given(searchHistoryRepository.countBySearcherId(SEARCHER_ID)).willReturn(5L, 4L, 3L);
        given(searchHistoryRepository.findFirstBySearcherIdOrderByIdAsc(SEARCHER_ID))
                .willReturn(Optional.of(mock(UserSearchHistory.class)));

        writer.replace(SEARCHER_ID, TARGET_ID, MAX_HISTORY_SIZE);

        verify(searchHistoryRepository, times(2)).findFirstBySearcherIdOrderByIdAsc(SEARCHER_ID);
        verify(searchHistoryRepository, times(2)).delete(any(UserSearchHistory.class));
    }

    @Test
    @DisplayName("최대 개수 이하면 지우지 않는다")
    void 이하면_삭제하지_않는다() {
        given(userRepository.getReferenceById(SEARCHER_ID)).willReturn(mock(User.class));
        given(userRepository.getReferenceById(TARGET_ID)).willReturn(mock(User.class));
        given(searchHistoryRepository.countBySearcherId(SEARCHER_ID)).willReturn(2L);

        writer.replace(SEARCHER_ID, TARGET_ID, MAX_HISTORY_SIZE);

        verify(searchHistoryRepository, never()).findFirstBySearcherIdOrderByIdAsc(SEARCHER_ID);
    }
}
