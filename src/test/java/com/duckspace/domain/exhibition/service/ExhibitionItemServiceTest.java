package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.dto.request.AddItemRequest;
import com.duckspace.domain.exhibition.dto.request.PlacementRequest;
import com.duckspace.domain.exhibition.dto.request.UpdatePositionRequest;
import com.duckspace.domain.exhibition.dto.response.ExhibitionItemPageResponse;
import com.duckspace.domain.exhibition.dto.response.ExhibitionItemResponse;
import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import com.duckspace.domain.exhibition.exception.ExhibitionErrorCode;
import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExhibitionItemServiceTest {

    private static final Long OWNER = 1L;
    private static final Long STRANGER = 99L;
    private static final Long EXHIBITION_ID = 10L;

    @Mock
    private ExhibitionItemRepository exhibitionItemRepository;

    @Mock
    private ExhibitionService exhibitionService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ExhibitionItemService exhibitionItemService;

    private Exhibition exhibition;

    @BeforeEach
    void setUp() {
        exhibition = new Exhibition(OWNER, "장식장1", null);
        ReflectionTestUtils.setField(exhibition, "id", EXHIBITION_ID);
    }

    private ExhibitionItem item(Long id) {
        ExhibitionItem i = new ExhibitionItem(
                exhibition, new ExhibitionItem.Placement(0.1, 0.2, 0.3, 0.3),
                "https://img/a.png", "굿즈", null, null, ItemStatus.READY);
        ReflectionTestUtils.setField(i, "id", id);
        return i;
    }

    private AddItemRequest addRequest() {
        return new AddItemRequest(
                new PlacementRequest(0.25, 0.4, 0.2, 0.3),
                "https://img/x.png", "치이카와 인형", 15000, "귀여움");
    }

    @Nested
    @DisplayName("add 메서드는")
    class Add {

        @Test
        void 좌표와_크기를_그대로_저장한다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.save(any(ExhibitionItem.class)))
                    .willAnswer(inv -> {
                        ExhibitionItem saved = inv.getArgument(0);
                        ReflectionTestUtils.setField(saved, "id", 7L);
                        return saved;
                    });

            ExhibitionItemResponse response = exhibitionItemService.add(EXHIBITION_ID, OWNER, addRequest());

            assertThat(response.itemId()).isEqualTo(7L);
            assertThat(response.posX()).isEqualTo(0.25);
            assertThat(response.posY()).isEqualTo(0.4);
            assertThat(response.width()).isEqualTo(0.2);
            assertThat(response.height()).isEqualTo(0.3);
            assertThat(response.price()).isEqualTo(15000);
        }

        @Test
        void 자유_배치라_위치가_겹쳐도_막지_않는다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.save(any(ExhibitionItem.class))).willReturn(item(7L));

            // 같은 좌표로 두 번 배치해도 예외가 없어야 합니다.
            exhibitionItemService.add(EXHIBITION_ID, OWNER, addRequest());
            exhibitionItemService.add(EXHIBITION_ID, OWNER, addRequest());

            verify(exhibitionItemRepository, org.mockito.Mockito.times(2)).save(any(ExhibitionItem.class));
        }

        @Test
        void 남의_장식장이면_막힌다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, STRANGER))
                    .willThrow(new BusinessException(ExhibitionErrorCode.NOT_EXHIBITION_OWNER));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionItemService.add(EXHIBITION_ID, STRANGER, addRequest()));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.NOT_EXHIBITION_OWNER);
            verify(exhibitionItemRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updatePosition 메서드는")
    class UpdatePosition {

        @Test
        void 이동_결과를_반영한다() {
            ExhibitionItem target = item(5L);
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findById(5L)).willReturn(Optional.of(target));

            ExhibitionItemResponse response = exhibitionItemService.updatePosition(
                    EXHIBITION_ID, 5L, OWNER,
                    new UpdatePositionRequest(new PlacementRequest(0.8, 0.9, 0.15, 0.15)));

            assertThat(response.posX()).isEqualTo(0.8);
            assertThat(response.posY()).isEqualTo(0.9);
            assertThat(target.getWidth()).isEqualTo(0.15);
        }

        @Test
        void 다른_장식장의_굿즈는_옮길_수_없다() {
            Exhibition other = new Exhibition(OWNER, "다른 장식장", null);
            ReflectionTestUtils.setField(other, "id", 77L);
            ExhibitionItem itemOfOther = new ExhibitionItem(
                    other, new ExhibitionItem.Placement(0.1, 0.1, 0.1, 0.1),
                    "url", "굿즈", null, null, ItemStatus.READY);
            ReflectionTestUtils.setField(itemOfOther, "id", 5L);

            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findById(5L)).willReturn(Optional.of(itemOfOther));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionItemService.updatePosition(EXHIBITION_ID, 5L, OWNER,
                            new UpdatePositionRequest(new PlacementRequest(0.8, 0.9, 0.15, 0.15))));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.ITEM_NOT_FOUND);
        }

        @Test
        void 남의_장식장이면_막힌다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, STRANGER))
                    .willThrow(new BusinessException(ExhibitionErrorCode.NOT_EXHIBITION_OWNER));

            assertThrows(BusinessException.class,
                    () -> exhibitionItemService.updatePosition(EXHIBITION_ID, 5L, STRANGER,
                            new UpdatePositionRequest(new PlacementRequest(0.1, 0.1, 0.1, 0.1))));

            verify(exhibitionItemRepository, never()).findById(anyLong());
        }
    }

    @Nested
    @DisplayName("upload 메서드는")
    class Upload {

        private org.springframework.mock.web.MockMultipartFile png(byte[] bytes) {
            return new org.springframework.mock.web.MockMultipartFile(
                    "image", "goods.png", "image/png", bytes);
        }

        private com.duckspace.domain.exhibition.dto.request.UploadItemRequest uploadRequest() {
            return new com.duckspace.domain.exhibition.dto.request.UploadItemRequest(
                    new PlacementRequest(0.3, 0.4, 0.2, 0.2), "치이카와 인형", 15000, "귀여움");
        }

        @Test
        void 접수만_하고_PENDING_으로_응답한다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.save(any(ExhibitionItem.class)))
                    .willAnswer(inv -> {
                        ExhibitionItem saved = inv.getArgument(0);
                        ReflectionTestUtils.setField(saved, "id", 7L);
                        return saved;
                    });

            ExhibitionItemResponse response =
                    exhibitionItemService.upload(EXHIBITION_ID, OWNER, png(new byte[]{1, 2, 3}), uploadRequest());

            assertThat(response.itemId()).isEqualTo(7L);
            assertThat(response.status()).isEqualTo(ItemStatus.PENDING);
            assertThat(response.imageUrl()).isNull();
            assertThat(response.posX()).isEqualTo(0.3);
        }

        @Test
        void 커밋_이후_처리되도록_이벤트를_발행한다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.save(any(ExhibitionItem.class)))
                    .willAnswer(inv -> {
                        ExhibitionItem saved = inv.getArgument(0);
                        ReflectionTestUtils.setField(saved, "id", 7L);
                        return saved;
                    });

            exhibitionItemService.upload(EXHIBITION_ID, OWNER, png(new byte[]{1, 2, 3}), uploadRequest());

            org.mockito.ArgumentCaptor<ItemImageUploadedEvent> captor =
                    org.mockito.ArgumentCaptor.forClass(ItemImageUploadedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().itemId()).isEqualTo(7L);
            assertThat(captor.getValue().fileName()).isEqualTo("goods.png");
        }

        @Test
        void 빈_파일이면_거부한다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionItemService.upload(EXHIBITION_ID, OWNER, png(new byte[0]), uploadRequest()));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.EMPTY_IMAGE);
            verify(eventPublisher, never()).publishEvent(any(ItemImageUploadedEvent.class));
        }

        @Test
        void 지원하지_않는_형식이면_거부한다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            var pdf = new org.springframework.mock.web.MockMultipartFile(
                    "image", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionItemService.upload(EXHIBITION_ID, OWNER, pdf, uploadRequest()));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }

    }

    @Nested
    @DisplayName("list 메서드는")
    class ListItems {

        @Test
        void 주인에게는_처리중인_굿즈도_보여준다() {
            given(exhibitionService.getExhibition(EXHIBITION_ID)).willReturn(exhibition);
            given(exhibitionItemRepository.findByExhibitionIdAndStatusInOrderByIdDesc(
                    eq(EXHIBITION_ID), any(), any(Pageable.class)))
                    .willReturn(new ArrayList<>(List.of(item(30L))));

            exhibitionItemService.list(EXHIBITION_ID, OWNER, null, 10);

            ArgumentCaptor<Collection<ItemStatus>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(exhibitionItemRepository).findByExhibitionIdAndStatusInOrderByIdDesc(
                    eq(EXHIBITION_ID), captor.capture(), any(Pageable.class));
            assertThat(captor.getValue()).containsExactlyInAnyOrderElementsOf(
                    Set.of(ItemStatus.PENDING, ItemStatus.READY, ItemStatus.FAILED));
        }

        @Test
        void 남에게는_완료된_굿즈만_보여준다() {
            given(exhibitionService.getExhibition(EXHIBITION_ID)).willReturn(exhibition);
            given(exhibitionItemRepository.findByExhibitionIdAndStatusInOrderByIdDesc(
                    eq(EXHIBITION_ID), any(), any(Pageable.class)))
                    .willReturn(new ArrayList<>(List.of(item(30L))));

            exhibitionItemService.list(EXHIBITION_ID, STRANGER, null, 10);

            ArgumentCaptor<Collection<ItemStatus>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(exhibitionItemRepository).findByExhibitionIdAndStatusInOrderByIdDesc(
                    eq(EXHIBITION_ID), captor.capture(), any(Pageable.class));
            assertThat(captor.getValue()).containsExactly(ItemStatus.READY);
        }

        @Test
        void 다음_페이지가_있으면_커서를_준다() {
            given(exhibitionService.getExhibition(EXHIBITION_ID)).willReturn(exhibition);
            given(exhibitionItemRepository.findByExhibitionIdAndStatusInOrderByIdDesc(
                    eq(EXHIBITION_ID), any(), any(Pageable.class)))
                    .willReturn(new ArrayList<>(List.of(item(30L), item(20L), item(10L))));

            ExhibitionItemPageResponse page = exhibitionItemService.list(EXHIBITION_ID, OWNER, null, 2);

            assertThat(page.items()).extracting(ExhibitionItemResponse::itemId).containsExactly(30L, 20L);
            assertThat(page.hasNext()).isTrue();
            assertThat(page.nextCursor()).isEqualTo(20L);
        }

        @Test
        void 커서가_있으면_그_이후만_조회한다() {
            given(exhibitionService.getExhibition(EXHIBITION_ID)).willReturn(exhibition);
            given(exhibitionItemRepository.findByExhibitionIdAndStatusInAndIdLessThanOrderByIdDesc(
                    eq(EXHIBITION_ID), any(), eq(20L), any(Pageable.class)))
                    .willReturn(new ArrayList<>(List.of(item(10L))));

            ExhibitionItemPageResponse page = exhibitionItemService.list(EXHIBITION_ID, OWNER, 20L, 2);

            assertThat(page.items()).extracting(ExhibitionItemResponse::itemId).containsExactly(10L);
            assertThat(page.hasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("delete 메서드는")
    class DeleteItem {

        @Test
        void 굿즈를_지운다() {
            ExhibitionItem target = item(5L);
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findById(5L)).willReturn(Optional.of(target));

            exhibitionItemService.delete(EXHIBITION_ID, 5L, OWNER);

            verify(exhibitionItemRepository).delete(target);
        }

        @Test
        void 없는_굿즈면_예외() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findById(5L)).willReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionItemService.delete(EXHIBITION_ID, 5L, OWNER));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.ITEM_NOT_FOUND);
        }
    }
}
