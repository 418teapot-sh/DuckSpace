package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.dto.request.CreateExhibitionRequest;
import com.duckspace.domain.exhibition.dto.request.UpdateExhibitionRequest;
import com.duckspace.domain.exhibition.dto.response.ExhibitionDetailResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionSummaryPageResponse;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Mock
    private com.duckspace.domain.exhibition.image.ImageCleanup imageCleanup;

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
    @DisplayName("createDefault 메서드는")
    class CreateDefault {

        @Test
        void 기본_이름과_기본_테마로_장식장을_만든다() {
            exhibitionService.createDefault(OWNER);

            ArgumentCaptor<Exhibition> captor = ArgumentCaptor.forClass(Exhibition.class);
            verify(exhibitionRepository).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(OWNER);
            assertThat(captor.getValue().getThemeCode()).isEqualTo(Exhibition.DEFAULT_THEME_CODE);
        }
    }

    @Nested
    @DisplayName("getDetail 메서드는")
    class GetDetail {

        @Test
        void 배치된_굿즈를_모두_돌려준다() {
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

        @Test
        void 저장된_이미지도_함께_정리한다() {
            given(exhibitionRepository.findById(EXHIBITION_ID)).willReturn(Optional.of(exhibition));
            given(exhibitionItemRepository.findImageUrlsByExhibitionId(EXHIBITION_ID))
                    .willReturn(List.of("https://cdn/a.png", "https://cdn/b.png"));

            exhibitionService.delete(EXHIBITION_ID, OWNER);

            // 행을 지운 뒤에는 이미지 주소를 알아낼 방법이 없어서, 미리 챙겨두지 않으면
            // S3 객체가 영구히 남습니다.
            verify(imageCleanup).deleteAfterCommit(List.of("https://cdn/a.png", "https://cdn/b.png"));
        }


    }

    @Nested
    @DisplayName("getMine 메서드는")
    class Mine {

        @Test
        void 내_장식장을_카드_형태로_돌려준다() {
            Exhibition e = exhibitionWithId(3L, OWNER, "내 장식장");
            ExhibitionItem itemA = itemOf(e, "thumb.png");
            ExhibitionItem itemB = itemOf(e, "second.png");

            given(exhibitionRepository.findIdsByUserId(eq(OWNER), any(Pageable.class)))
                    .willReturn(List.of(3L));
            given(exhibitionRepository.findAllById(List.of(3L))).willReturn(List.of(e));
            given(exhibitionItemRepository.findAllByExhibitionIdsAndStatus(List.of(3L), ItemStatus.READY))
                    .willReturn(List.of(itemA, itemB));
            given(exhibitionLikeRepository.countByExhibitionIds(List.of(3L))).willReturn(List.of());
            given(exhibitionLikeRepository.findLikedExhibitionIds(OWNER, List.of(3L))).willReturn(List.of());

            List<ExhibitionSummaryResponse> result = exhibitionService.getMine(OWNER, null, null);

            assertThat(result).extracting(ExhibitionSummaryResponse::exhibitionId).containsExactly(3L);
            assertThat(result.get(0).thumbnailUrl()).isEqualTo("thumb.png");
            assertThat(result.get(0).items())
                    .as("카드 미리보기는 배치된 굿즈 전체를 그대로 담아야 합니다")
                    .extracting(r -> r.imageUrl())
                    .containsExactly("thumb.png", "second.png");
        }

        @Test
        void limit을_주지_않으면_20개를_가져온다() {
            given(exhibitionRepository.findIdsByUserId(eq(OWNER), any(Pageable.class)))
                    .willReturn(List.of());

            exhibitionService.getMine(OWNER, null, null);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(exhibitionRepository).findIdsByUserId(eq(OWNER), captor.capture());
            assertThat(captor.getValue().getPageSize())
                    .as("내 장식장은 한 화면에 다 보이는 편이 자연스럽습니다")
                    .isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("getByUser 메서드는")
    class ByUser {

        @Test
        @DisplayName("조회 대상(userId)이 아니라 보는 사람(viewerId) 기준으로 likedByMe 를 채운다")
        void viewerId_기준으로_likedByMe를_채운다() {
            Exhibition e = exhibitionWithId(3L, OWNER, "남의 장식장");

            given(exhibitionRepository.findIdsByUserId(eq(OWNER), any(Pageable.class)))
                    .willReturn(List.of(3L));
            given(exhibitionRepository.findAllById(List.of(3L))).willReturn(List.of(e));
            given(exhibitionItemRepository.findAllByExhibitionIdsAndStatus(List.of(3L), ItemStatus.READY))
                    .willReturn(List.of());
            given(exhibitionLikeRepository.countByExhibitionIds(List.of(3L))).willReturn(List.of());
            given(exhibitionLikeRepository.findLikedExhibitionIds(STRANGER, List.of(3L))).willReturn(List.of(3L));

            List<ExhibitionSummaryResponse> result = exhibitionService.getByUser(OWNER, STRANGER, null);

            assertThat(result).extracting(ExhibitionSummaryResponse::exhibitionId).containsExactly(3L);
            assertThat(result.get(0).likedByMe())
                    .as("본인(OWNER)이 아니라 보는 사람(STRANGER)의 좋아요 여부여야 합니다")
                    .isTrue();
        }

        @Test
        @DisplayName("비로그인(viewerId=null)이어도 좋아요 여부만 false 로 채워 돌려준다")
        void 비로그인도_조회할_수_있다() {
            Exhibition e = exhibitionWithId(3L, OWNER, "남의 장식장");

            given(exhibitionRepository.findIdsByUserId(eq(OWNER), any(Pageable.class)))
                    .willReturn(List.of(3L));
            given(exhibitionRepository.findAllById(List.of(3L))).willReturn(List.of(e));
            given(exhibitionItemRepository.findAllByExhibitionIdsAndStatus(List.of(3L), ItemStatus.READY))
                    .willReturn(List.of());
            given(exhibitionLikeRepository.countByExhibitionIds(List.of(3L))).willReturn(List.of());
            given(exhibitionLikeRepository.findLikedExhibitionIds(null, List.of(3L))).willReturn(List.of());

            List<ExhibitionSummaryResponse> result = exhibitionService.getByUser(OWNER, null, null);

            assertThat(result.get(0).likedByMe()).isFalse();
        }

        @Test
        @DisplayName("장식장이 하나도 없으면 예외 없이 빈 목록을 돌려준다")
        void 장식장이_없으면_빈목록() {
            given(exhibitionRepository.findIdsByUserId(eq(OWNER), any(Pageable.class)))
                    .willReturn(List.of());

            List<ExhibitionSummaryResponse> result = exhibitionService.getByUser(OWNER, STRANGER, null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("limit을 주지 않으면 getMine 과 같은 기본값(20)을 쓴다")
        void limit을_주지_않으면_20개를_가져온다() {
            given(exhibitionRepository.findIdsByUserId(eq(OWNER), any(Pageable.class)))
                    .willReturn(List.of());

            exhibitionService.getByUser(OWNER, STRANGER, null);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(exhibitionRepository).findIdsByUserId(eq(OWNER), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("getPrimary 메서드는")
    class Primary {

        @Test
        void 가장_먼저_만든_장식장을_돌려준다() {
            Exhibition e = exhibitionWithId(3L, OWNER, "내 장식장");

            given(exhibitionRepository.findIdsByUserId(eq(OWNER), any(Pageable.class)))
                    .willReturn(List.of(3L));
            given(exhibitionRepository.findAllById(List.of(3L))).willReturn(List.of(e));
            given(exhibitionLikeRepository.countByExhibitionIds(List.of(3L))).willReturn(List.of());
            given(exhibitionLikeRepository.findLikedExhibitionIds(STRANGER, List.of(3L))).willReturn(List.of());

            ExhibitionSummaryResponse result = exhibitionService.getPrimary(OWNER, STRANGER);

            assertThat(result.exhibitionId()).isEqualTo(3L);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(exhibitionRepository).findIdsByUserId(eq(OWNER), captor.capture());
            assertThat(captor.getValue().getPageSize())
                    .as("대표 장식장은 하나만 필요합니다")
                    .isEqualTo(1);
        }

        @Test
        void 장식장이_하나도_없으면_예외() {
            given(exhibitionRepository.findIdsByUserId(eq(OWNER), any(Pageable.class)))
                    .willReturn(List.of());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionService.getPrimary(OWNER, STRANGER));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.EXHIBITION_NOT_FOUND);
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
            given(exhibitionItemRepository.findAllByExhibitionIdsAndStatus(List.of(3L, 1L), ItemStatus.READY))
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
        @DisplayName("굿즈는 각 장식장 자기 것만 들어간다 — 다른 장식장 것과 안 섞인다")
        void 굿즈는_장식장별로_섞이지_않는다() {
            Exhibition first = exhibitionWithId(3L, 2L, "인기1위");
            Exhibition second = exhibitionWithId(1L, 3L, "인기2위");
            ExhibitionItem firstItem = itemOf(first, "first.png");
            ExhibitionItem secondItem = itemOf(second, "second.png");

            given(exhibitionRepository.findPopularIds(any(Pageable.class))).willReturn(List.of(3L, 1L));
            given(exhibitionRepository.findAllById(List.of(3L, 1L))).willReturn(List.of(first, second));
            given(exhibitionItemRepository.findAllByExhibitionIdsAndStatus(List.of(3L, 1L), ItemStatus.READY))
                    .willReturn(List.of(firstItem, secondItem));
            given(exhibitionLikeRepository.countByExhibitionIds(List.of(3L, 1L))).willReturn(List.of());
            given(exhibitionLikeRepository.findLikedExhibitionIds(OWNER, List.of(3L, 1L))).willReturn(List.of());

            List<ExhibitionSummaryResponse> result = exhibitionService.getPopular(10, OWNER);

            assertThat(result.get(0).items()).extracting(r -> r.imageUrl()).containsExactly("first.png");
            assertThat(result.get(1).items()).extracting(r -> r.imageUrl()).containsExactly("second.png");
        }

        @Test
        @DisplayName("비로그인(viewerId=null)이어도 좋아요 여부만 false 로 채워 돌려준다")
        void 비로그인도_조회할_수_있다() {
            // 홈 화면(/api/home)이 인증 없이 열려서 viewerId 가 null 로 들어옵니다.
            Exhibition e = exhibitionWithId(3L, 2L, "인기1위");

            given(exhibitionRepository.findPopularIds(any(Pageable.class))).willReturn(List.of(3L));
            given(exhibitionRepository.findAllById(List.of(3L))).willReturn(List.of(e));
            given(exhibitionLikeRepository.countByExhibitionIds(List.of(3L))).willReturn(List.of());
            given(exhibitionLikeRepository.findLikedExhibitionIds(null, List.of(3L))).willReturn(List.of());

            List<ExhibitionSummaryResponse> result = exhibitionService.getPopular(10, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).likedByMe()).isFalse();
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
    @DisplayName("getRecent 메서드는")
    class Recent {

        @Test
        @DisplayName("cursor 가 null 이면 리포지토리에도 null 로 넘긴다")
        void cursor가_null이면_그대로_null() {
            given(exhibitionRepository.findRecentIds(isNull(), any(Pageable.class))).willReturn(List.of());

            exhibitionService.getRecent(null, 10, OWNER);

            verify(exhibitionRepository).findRecentIds(isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("cursor 가 0 이하면 첫 페이지로 취급해 null 로 정규화한다")
        void cursor가_0이하면_null로_정규화한다() {
            // 장식장 id 는 1부터 시작해서, 0 이하를 그대로 넘기면 e.id < 0 이 되어 데이터가
            // 있어도 항상 빈 목록이 나옵니다(PR #86 리뷰) — 여기서 null 로 정규화해 막습니다.
            given(exhibitionRepository.findRecentIds(isNull(), any(Pageable.class))).willReturn(List.of());

            exhibitionService.getRecent(0L, 10, OWNER);
            exhibitionService.getRecent(-5L, 10, OWNER);

            verify(exhibitionRepository, times(2)).findRecentIds(isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("유효한 cursor(1 이상)는 그대로 리포지토리에 넘긴다")
        void 유효한_cursor는_그대로_넘긴다() {
            given(exhibitionRepository.findRecentIds(eq(5L), any(Pageable.class))).willReturn(List.of());

            exhibitionService.getRecent(5L, 10, OWNER);

            verify(exhibitionRepository).findRecentIds(eq(5L), any(Pageable.class));
        }

        @Test
        @DisplayName("다음 페이지가 있으면 hasNext 와 nextCursor 를 채운다")
        void 다음_페이지가_있으면_hasNext를_채운다() {
            Exhibition newest = exhibitionWithId(3L, 2L, "최신");

            given(exhibitionRepository.findRecentIds(isNull(), any(Pageable.class))).willReturn(List.of(3L, 2L));
            given(exhibitionRepository.findAllById(List.of(3L))).willReturn(List.of(newest));
            given(exhibitionItemRepository.findAllByExhibitionIdsAndStatus(List.of(3L), ItemStatus.READY))
                    .willReturn(List.of());
            given(exhibitionLikeRepository.countByExhibitionIds(List.of(3L))).willReturn(List.of());
            given(exhibitionLikeRepository.findLikedExhibitionIds(OWNER, List.of(3L))).willReturn(List.of());

            ExhibitionSummaryPageResponse result = exhibitionService.getRecent(null, 1, OWNER);

            assertThat(result.items()).hasSize(1);
            assertThat(result.hasNext()).isTrue();
            assertThat(result.nextCursor()).isEqualTo(3L);
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
            verify(exhibitionItemRepository, never()).findAllByExhibitionIdsAndStatus(any(), any());
        }
    }
}
