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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserSearchServiceTest {

    private static final Long SEARCHER_ID = 1L;
    private static final Long TARGET_ID = 2L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSearchHistoryRepository searchHistoryRepository;

    @InjectMocks
    private UserSearchService userSearchService;

    @Test
    @DisplayName("키워드가 비어 있으면 조회 없이 빈 목록을 돌려준다")
    void 빈_키워드는_빈_목록() {
        assertThat(userSearchService.search("", 10)).isEmpty();
        assertThat(userSearchService.search(null, 10)).isEmpty();
    }

    @Test
    @DisplayName("자기 자신을 클릭한 기록은 무시한다")
    void 자기_자신_클릭은_무시() {
        userSearchService.record(SEARCHER_ID, SEARCHER_ID);

        verify(searchHistoryRepository, never()).save(any(UserSearchHistory.class));
    }

    @Test
    @DisplayName("이미 있던 항목을 다시 클릭하면 지우고 다시 넣어서 맨 위로 올린다")
    void 중복_클릭은_맨_위로_이동() {
        given(userRepository.findById(SEARCHER_ID)).willReturn(Optional.of(mock(User.class)));
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(mock(User.class)));
        given(searchHistoryRepository.countBySearcherId(SEARCHER_ID)).willReturn(1L);

        userSearchService.record(SEARCHER_ID, TARGET_ID);

        verify(searchHistoryRepository).deleteBySearcherIdAndSearchedUserId(SEARCHER_ID, TARGET_ID);
        verify(searchHistoryRepository, times(1)).save(any(UserSearchHistory.class));
    }

    @Test
    @DisplayName("3개를 넘기면 가장 오래된 항목을 지운다")
    void 세개_초과시_가장_오래된_항목_삭제() {
        given(userRepository.findById(SEARCHER_ID)).willReturn(Optional.of(mock(User.class)));
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(mock(User.class)));
        given(searchHistoryRepository.countBySearcherId(SEARCHER_ID)).willReturn(4L);
        given(searchHistoryRepository.findFirstBySearcherIdOrderByIdAsc(SEARCHER_ID))
                .willReturn(Optional.of(mock(UserSearchHistory.class)));

        userSearchService.record(SEARCHER_ID, TARGET_ID);

        verify(searchHistoryRepository).findFirstBySearcherIdOrderByIdAsc(SEARCHER_ID);
        verify(searchHistoryRepository).delete(any(UserSearchHistory.class));
    }

    @Test
    @DisplayName("3개 이하면 오래된 항목을 지우지 않는다")
    void 세개_이하면_삭제하지_않는다() {
        given(userRepository.findById(SEARCHER_ID)).willReturn(Optional.of(mock(User.class)));
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(mock(User.class)));
        given(searchHistoryRepository.countBySearcherId(SEARCHER_ID)).willReturn(3L);

        userSearchService.record(SEARCHER_ID, TARGET_ID);

        verify(searchHistoryRepository, never()).findFirstBySearcherIdOrderByIdAsc(SEARCHER_ID);
    }

    @Test
    @DisplayName("전체 삭제는 본인 검색 내역만 지운다")
    void 전체_삭제() {
        userSearchService.clearHistory(SEARCHER_ID);

        verify(searchHistoryRepository).deleteAllBySearcherId(SEARCHER_ID);
    }
}
