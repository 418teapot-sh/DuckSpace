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
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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

    private void 참조_준비() {
        given(userRepository.getReferenceById(SEARCHER_ID)).willReturn(mock(User.class));
        given(userRepository.getReferenceById(TARGET_ID)).willReturn(mock(User.class));
    }

    @Test
    @DisplayName("삭제하고 새로 저장한 뒤, 최신 N개만 남기고 나머지를 한 번에 지운다")
    void 삭제_저장_트리밍_순서() {
        참조_준비();
        given(searchHistoryRepository.findIdsBySearcherIdOrderByIdDesc(eq(SEARCHER_ID), any(PageRequest.class)))
                .willReturn(List.of(10L, 9L, 8L));

        writer.replace(SEARCHER_ID, TARGET_ID, MAX_HISTORY_SIZE);

        verify(searchHistoryRepository).deleteBySearcherIdAndSearchedUserId(SEARCHER_ID, TARGET_ID);
        verify(searchHistoryRepository).saveAndFlush(any(UserSearchHistory.class));
        verify(searchHistoryRepository).deleteBySearcherIdAndIdNotIn(SEARCHER_ID, List.of(10L, 9L, 8L));
    }

    @Test
    @DisplayName("트리밍은 반복이 아니라 단일 벌크 삭제 한 번뿐이다 — 이전 while-루프의 무한루프 재발을 막는 회귀 테스트")
    void 트리밍은_단일_호출이다() {
        참조_준비();
        given(searchHistoryRepository.findIdsBySearcherIdOrderByIdDesc(eq(SEARCHER_ID), any(PageRequest.class)))
                .willReturn(List.of(1L));

        writer.replace(SEARCHER_ID, TARGET_ID, MAX_HISTORY_SIZE);

        verify(searchHistoryRepository, times(1))
                .findIdsBySearcherIdOrderByIdDesc(eq(SEARCHER_ID), any(PageRequest.class));
        verify(searchHistoryRepository, times(1))
                .deleteBySearcherIdAndIdNotIn(eq(SEARCHER_ID), any());
    }

    @Test
    @DisplayName("exists는 재확인용 조회를 그대로 위임한다")
    void exists_재확인() {
        given(searchHistoryRepository.existsBySearcherIdAndSearchedUserId(SEARCHER_ID, TARGET_ID)).willReturn(true);

        assertThat(writer.exists(SEARCHER_ID, TARGET_ID)).isTrue();
    }
}
