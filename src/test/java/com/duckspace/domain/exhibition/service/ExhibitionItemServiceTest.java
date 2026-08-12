package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.dto.request.AddItemRequest;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
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
class ExhibitionItemServiceTest {

    private static final Long OWNER = 1L;
    private static final Long STRANGER = 99L;
    private static final Long EXHIBITION_ID = 10L;

    @Mock
    private ExhibitionItemRepository exhibitionItemRepository;

    @Mock
    private ExhibitionService exhibitionService;

    @InjectMocks
    private ExhibitionItemService exhibitionItemService;

    private Exhibition exhibition;

    @BeforeEach
    void setUp() {
        exhibition = new Exhibition(OWNER, "장식장1");
        ReflectionTestUtils.setField(exhibition, "id", EXHIBITION_ID);
    }

    private ExhibitionItem item(Long id, String slotId) {
        ExhibitionItem i = new ExhibitionItem(
                exhibition, slotId, "https://img/" + slotId + ".png", "굿즈", null, null, ItemStatus.READY);
        ReflectionTestUtils.setField(i, "id", id);
        return i;
    }

    private AddItemRequest request(String slotId) {
        return new AddItemRequest(slotId, "https://img/x.png", "치이카와 인형", "산리오", "귀여움");
    }

    @Nested
    @DisplayName("add 메서드는")
    class Add {

        @Test
        void 슬롯에_굿즈를_배치한다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.existsByExhibitionIdAndSlotId(EXHIBITION_ID, "SHELF_1")).willReturn(false);
            given(exhibitionItemRepository.saveAndFlush(any(ExhibitionItem.class))).willReturn(item(5L, "SHELF_1"));

            ExhibitionItemResponse response = exhibitionItemService.add(EXHIBITION_ID, OWNER, request("SHELF_1"));

            assertThat(response.itemId()).isEqualTo(5L);
            assertThat(response.slotId()).isEqualTo("SHELF_1");
            assertThat(response.status()).isEqualTo(ItemStatus.READY);
        }

        @Test
        void 이미_굿즈가_놓인_슬롯이면_거부한다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.existsByExhibitionIdAndSlotId(EXHIBITION_ID, "SHELF_1")).willReturn(true);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionItemService.add(EXHIBITION_ID, OWNER, request("SHELF_1")));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.SLOT_ALREADY_OCCUPIED);
            verify(exhibitionItemRepository, never()).saveAndFlush(any());
        }

        @Test
        void 동시_배치로_제약에_걸려도_409로_바꿔준다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.existsByExhibitionIdAndSlotId(EXHIBITION_ID, "SHELF_1")).willReturn(false);
            given(exhibitionItemRepository.saveAndFlush(any(ExhibitionItem.class)))
                    .willThrow(new DataIntegrityViolationException("uk_exhibition_item_slot"));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionItemService.add(EXHIBITION_ID, OWNER, request("SHELF_1")));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.SLOT_ALREADY_OCCUPIED);
        }

        @Test
        void 남의_장식장이면_소유자_확인에서_막힌다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, STRANGER))
                    .willThrow(new BusinessException(ExhibitionErrorCode.NOT_EXHIBITION_OWNER));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionItemService.add(EXHIBITION_ID, STRANGER, request("SHELF_1")));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.NOT_EXHIBITION_OWNER);
            verify(exhibitionItemRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("list 메서드는")
    class ListItems {

        @Test
        void 다음_페이지가_있으면_커서를_준다() {
            given(exhibitionService.getExhibition(EXHIBITION_ID)).willReturn(exhibition);
            // size=2 요청이면 서비스가 3개를 조회해 존재 여부를 확인합니다.
            List<ExhibitionItem> found = new ArrayList<>(
                    List.of(item(30L, "A"), item(20L, "B"), item(10L, "C")));
            given(exhibitionItemRepository.findByExhibitionIdOrderByIdDesc(eq(EXHIBITION_ID), any(Pageable.class)))
                    .willReturn(found);

            ExhibitionItemPageResponse page = exhibitionItemService.list(EXHIBITION_ID, null, 2);

            assertThat(page.items()).extracting(ExhibitionItemResponse::itemId).containsExactly(30L, 20L);
            assertThat(page.hasNext()).isTrue();
            assertThat(page.nextCursor()).isEqualTo(20L);
        }

        @Test
        void 마지막_페이지면_커서가_없다() {
            given(exhibitionService.getExhibition(EXHIBITION_ID)).willReturn(exhibition);
            given(exhibitionItemRepository.findByExhibitionIdOrderByIdDesc(eq(EXHIBITION_ID), any(Pageable.class)))
                    .willReturn(new ArrayList<>(List.of(item(30L, "A"))));

            ExhibitionItemPageResponse page = exhibitionItemService.list(EXHIBITION_ID, null, 2);

            assertThat(page.hasNext()).isFalse();
            assertThat(page.nextCursor()).isNull();
        }

        @Test
        void 커서가_있으면_그_이후만_조회한다() {
            given(exhibitionService.getExhibition(EXHIBITION_ID)).willReturn(exhibition);
            given(exhibitionItemRepository.findByExhibitionIdAndIdLessThanOrderByIdDesc(
                    eq(EXHIBITION_ID), eq(20L), any(Pageable.class)))
                    .willReturn(new ArrayList<>(List.of(item(10L, "C"))));

            ExhibitionItemPageResponse page = exhibitionItemService.list(EXHIBITION_ID, 20L, 2);

            assertThat(page.items()).extracting(ExhibitionItemResponse::itemId).containsExactly(10L);
            verify(exhibitionItemRepository, never())
                    .findByExhibitionIdOrderByIdDesc(anyLong(), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("delete 메서드는")
    class DeleteItem {

        @Test
        void 굿즈를_지운다() {
            ExhibitionItem target = item(5L, "SHELF_1");
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findById(5L)).willReturn(Optional.of(target));

            exhibitionItemService.delete(EXHIBITION_ID, 5L, OWNER);

            verify(exhibitionItemRepository).delete(target);
        }

        @Test
        void 다른_장식장의_굿즈_id를_넣으면_지워지지_않는다() {
            Exhibition other = new Exhibition(OWNER, "다른 장식장");
            ReflectionTestUtils.setField(other, "id", 77L);
            ExhibitionItem itemOfOther = new ExhibitionItem(
                    other, "SHELF_1", "url", "굿즈", null, null, ItemStatus.READY);
            ReflectionTestUtils.setField(itemOfOther, "id", 5L);

            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findById(5L)).willReturn(Optional.of(itemOfOther));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionItemService.delete(EXHIBITION_ID, 5L, OWNER));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.ITEM_NOT_FOUND);
            verify(exhibitionItemRepository, never()).delete(any());
        }
    }
}
