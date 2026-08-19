package com.duckspace.domain.user.service;

import com.duckspace.domain.user.entity.User;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.domain.user.repository.UserSearchHistoryRepository;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 실제 삭제/INSERT/트리밍 로직은 {@link UserSearchHistoryWriter}에 있어서(별도 트랜잭션이 필요한
 * 이유는 그 클래스 문서 참고) 여기서는 writer를 목으로 두고 record()가 언제 호출/무시하는지만 봅니다.
 * writer 자체의 트리밍 로직은 {@link UserSearchHistoryWriterTest}, 전체 흐름의 실제 DB 동작은
 * {@link UserSearchServiceIntegrationTest} 참고.
 */
@ExtendWith(MockitoExtension.class)
class UserSearchServiceTest {

    private static final Long SEARCHER_ID = 1L;
    private static final Long TARGET_ID = 2L;
    private static final int MAX_HISTORY_SIZE = 3;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSearchHistoryRepository searchHistoryRepository;

    @Mock
    private UserSearchHistoryWriter searchHistoryWriter;

    @InjectMocks
    private UserSearchService userSearchService;

    private void 유저_둘_다_존재() {
        User searcher = mock(User.class);
        User target = mock(User.class);
        given(userRepository.findAllById(List.of(SEARCHER_ID, TARGET_ID)))
                .willReturn(List.of(searcher, target));
        given(searcher.getId()).willReturn(SEARCHER_ID);
        given(target.getId()).willReturn(TARGET_ID);
    }

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

        verify(searchHistoryWriter, never()).replace(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("존재하는 유저끼리면 writer에 최대 개수와 함께 기록을 위임한다")
    void 기록은_writer에게_위임() {
        유저_둘_다_존재();

        userSearchService.record(SEARCHER_ID, TARGET_ID);

        verify(searchHistoryWriter, times(1)).replace(SEARCHER_ID, TARGET_ID, MAX_HISTORY_SIZE);
    }

    @Test
    @DisplayName("동시에 같은 조합이 다시 클릭돼 유니크 제약에 걸려도(원하는 상태는 이미 달성됨) 예외를 밖으로 던지지 않는다")
    void 동시_중복_클릭은_예외를_삼킨다() {
        유저_둘_다_존재();
        willThrow(new DataIntegrityViolationException("duplicate key"))
                .given(searchHistoryWriter).replace(SEARCHER_ID, TARGET_ID, MAX_HISTORY_SIZE);

        assertDoesNotThrow(() -> userSearchService.record(SEARCHER_ID, TARGET_ID));
    }

    @Test
    @DisplayName("존재하지 않는 유저를 대상으로 기록하면 USER_NOT_FOUND")
    void 없는_유저는_예외() {
        given(userRepository.findAllById(List.of(SEARCHER_ID, TARGET_ID))).willReturn(List.of());

        assertThrows(BusinessException.class, () -> userSearchService.record(SEARCHER_ID, TARGET_ID));

        verify(searchHistoryWriter, never()).replace(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("전체 삭제는 본인 검색 내역만 지운다")
    void 전체_삭제() {
        userSearchService.clearHistory(SEARCHER_ID);

        verify(searchHistoryRepository).deleteAllBySearcherId(SEARCHER_ID);
    }
}
