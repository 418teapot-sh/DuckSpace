package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.dto.request.CreateExhibitionRequest;
import com.duckspace.domain.exhibition.dto.request.UpdateExhibitionRequest;
import com.duckspace.domain.exhibition.dto.response.ExhibitionDetailResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionSummaryResponse;
import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import com.duckspace.domain.exhibition.repository.ExhibitionLikeRepository;
import com.duckspace.domain.exhibition.repository.ExhibitionRepository;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExhibitionServiceTest {

    private static final Long OWNER = 1L;
    private static final Long STRANGER = 99L;
    private static final Long EXHIBITION_ID = 10L;

    @Mock
    private ExhibitionRepository exhibitionRepository;

    @Mock
    private ExhibitionItemRepository exhibitionItemRepository;

    @Mock
    private ExhibitionLikeRepository exhibitionLikeRepository;

    @InjectMocks
    private ExhibitionService exhibitionService;

    private Exhibition exhibition;

    @BeforeEach
    void setUp() {
        exhibition = new Exhibition(OWNER, "장식장1", null);
        ReflectionTestUtils.setField(exhibition, "id", EXHIBITION_ID);
    }

    private Exhibition exhibitionWithId(Long id, Long userId, String name) {
        Exhibition e = new Exhibition(userId, name, null);
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private ExhibitionItem itemOf(Exhibition e, String imageUrl) {
        return new ExhibitionItem(e, new ExhibitionItem.Placement(0.1, 0.2, 0.3, 0.3),
                imageUrl, "굿즈", null, null, ItemStatus.READY);
    }

    @Nested
    @DisplayName("create 메서드는")
    class Create {

        @Test
        void 장식장을_만들고_빈_상태로_돌려준다() {
            given(exhibitionRepository.save(any(Exhibition.class))).willReturn(exhibition);

            ExhibitionDetailResponse response =
                    exhibitionService.create(OWNER, new CreateExhibitionRequest("장식장1", null));

            assertThat(response.exhibitionId()).isEqualTo(EXHIBITION_ID);
            assertThat(response.name()).isEqualTo("장식장1");
            assertThat(response.mine()).isTrue();
            assertThat(response.likeCount()).isZero();
            assertThat(response.items()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getDetail 메서드는")
    class GetDetail {

        @Test
        void 슬롯에_놓인_굿즈를_모두_돌려준다() {
            given(exhibitionRepository.findById(EXHIBITION_ID)).willReturn(Optional.of(exhibition));
            given(exhibitionItemRepository.findByExhibitionIdAndStatusInOrderByIdAsc(eq(EXHIBITION_ID), any()))
                    .willReturn(List.of(itemOf(exhibition, "a.png"),
                            itemOf(exhibition, "b.png")));
            given(exhibitionLikeRepository.countByExhibitionId(EXHIBITION_ID)).willReturn(0L);
            given(exhibitionLikeRepository.existsByExhibitionIdAndUserId(EXHIBITION_ID, OWNER)).willReturn(false);

            ExhibitionDetailResponse response = exhibitionService.getDetail(EXHIBITION_ID, OWNER);

            assertThat(response.items()).hasSize(2);
            assertThat(response.items()).extracting(r -> r.imageUrl()).containsExactly("a.png", "b.png");
        }

        @Test
        void 남의_장식장이면_mine이_false다() {
            given(exhibitionRepository.findById(EXHIBITION_ID)).willReturn(Optional.of(exhibition));
            given(exhibitionItemRepository.findByExhibitionIdAndStatusInOrderByIdAsc(eq(EXHIBITION_ID), any()))
                    .willReturn(List.of());
            given(exhibitionLikeRepository.countByExhibitionId(EXHIBITION_ID)).willReturn(0L);
            given(exhibitionLikeRepository.existsByExhibitionIdAndUserId(EXHIBITION_ID, STRANGER)).willReturn(true);

            ExhibitionDetailResponse response = exhibitionService.getDetail(EXHIBITION_ID, STRANGER);

            assertThat(response.mine()).isFalse();
            assertThat(response.likedByMe()).isTrue();
        }

        @Test
        void 없는_장식장이면_예외() {
            given(exhibitionRepository.findById(EXHIBITION_ID)).willReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionService.getDetail(EXHIBITION_ID, OWNER));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.EXHIBITION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("소유자 확인은")
    class Ownership {

        @Test
        void 남이_이름을_바꾸려_하면_막는다() {
            given(exhibitionRepository.findById(EXHIBITION_ID)).willReturn(Optional.of(exhibition));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionService.rename(EXHIBITION_ID, STRANGER, new UpdateExhibitionRequest("바꾼이름", null)));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.NOT_EXHIBITION_OWNER);
            assertThat(exhibition.getName()).isEqualTo("장식장1");
        }

        @Test
        void 남이_삭제하려_하면_막는다() {
            given(exhibitionRepository.findById(EXHIBITION_ID)).willReturn(Optional.of(exhibition));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionService.delete(EXHIBITION_ID, STRANGER));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.NOT_EXHIBITION_OWNER);
            verify(exhibitionRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("delete 메서드는")
    class Delete {

        @Test
        void 안에_놓인_굿즈와_좋아요도_함께_지운다() {
            given(exhibitionRepository.findById(EXHIBITION_ID)).willReturn(Optional.of(exhibition));

            exhibitionService.delete(EXHIBITION_ID, OWNER);

            verify(exhibitionItemRepository).deleteByExhibitionId(EXHIBITION_ID);
            verify(exhibitionLikeRepository).deleteByExhibitionId(EXHIBITION_ID);
            verify(exhibitionRepository).delete(exhibition);
        }
    }

    @Nested
    @DisplayName("getPopular 메서드는")
    class Popular {

        @Test
        void 인기순_id_순서를_그대로_유지한다() {
            Exhibition first = exhibitionWithId(3L, 2L, "인기1위");
            Exhibition second = exhibitionWithId(1L, 3L, "인기2위");

            given(exhibitionRepository.findPopularIds(any(Pageable.class))).willReturn(List.of(3L, 1L));
            // findAllById 는 순서를 보장하지 않으므로 일부러 뒤집어서 돌려줍니다.
            given(exhibitionRepository.findAllById(List.of(3L, 1L))).willReturn(List.of(second, first));
            given(exhibitionItemRepository.findFirstItemOfEach(List.of(3L, 1L), ItemStatus.READY))
                    .willReturn(List.of(itemOf(first, "thumb.png")));
            given(exhibitionLikeRepository.countByExhibitionIds(List.of(3L, 1L))).willReturn(List.of());
            given(exhibitionLikeRepository.findLikedExhibitionIds(OWNER, List.of(3L, 1L))).willReturn(List.of(3L));

            List<ExhibitionSummaryResponse> result = exhibitionService.getPopular(10, OWNER);

            assertThat(result).extracting(ExhibitionSummaryResponse::exhibitionId).containsExactly(3L, 1L);
            assertThat(result.get(0).thumbnailUrl()).isEqualTo("thumb.png");
            assertThat(result.get(0).likedByMe()).isTrue();
            assertThat(result.get(1).thumbnailUrl()).isNull();
            assertThat(result.get(1).likedByMe()).isFalse();
        }

        @Test
        void limit은_최대치를_넘지_않는다() {
            given(exhibitionRepository.findPopularIds(any(Pageable.class))).willReturn(List.of());

            exhibitionService.getPopular(999, OWNER);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(exhibitionRepository).findPopularIds(captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("search 메서드는")
    class Search {

        @Test
        void 키워드가_비어있으면_조회하지_않는다() {
            assertThat(exhibitionService.search("   ", 10, OWNER)).isEmpty();
            assertThat(exhibitionService.search(null, 10, OWNER)).isEmpty();
            verify(exhibitionRepository, never())
                    .searchExhibitionIdsByItem(any(), any(), any());
        }

        @Test
        void 와일드카드_문자를_이스케이프해서_넘긴다() {
            given(exhibitionRepository.searchExhibitionIdsByItem(eq("\\%"), eq(ItemStatus.READY), any(Pageable.class)))
                    .willReturn(List.of());

            exhibitionService.search("%", 10, OWNER);

            // eq("\\%") 로 스텁이 잡혔다는 것 자체가 이스케이프를 확인해 줍니다.
            verify(exhibitionRepository).searchExhibitionIdsByItem(eq("\\%"), eq(ItemStatus.READY), any(Pageable.class));
        }

        @Test
        void 언더스코어도_이스케이프한다() {
            given(exhibitionRepository.searchExhibitionIdsByItem(eq("\\_"), eq(ItemStatus.READY), any(Pageable.class)))
                    .willReturn(List.of());

            exhibitionService.search("_", 10, OWNER);

            verify(exhibitionRepository).searchExhibitionIdsByItem(eq("\\_"), eq(ItemStatus.READY), any(Pageable.class));
        }

        @Test
        void 결과가_없으면_추가_조회를_하지_않는다() {
            given(exhibitionRepository.searchExhibitionIdsByItem(any(), any(), any(Pageable.class)))
                    .willReturn(List.of());

            assertThat(exhibitionService.search("없는키워드", 10, OWNER)).isEmpty();
            verify(exhibitionRepository, never()).findAllById(any());
            verify(exhibitionItemRepository, never()).findFirstItemOfEach(any(), any());
        }
    }
}
