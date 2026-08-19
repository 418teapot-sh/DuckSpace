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
    private com.duckspace.domain.exhibition.image.ImageCleanup imageCleanup;

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
                new PlacementRequest(0.25, 0.4, 0.2, 0.3, 0.0),
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
        void 회전_각도를_그대로_저장한다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.save(any(ExhibitionItem.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ExhibitionItemResponse response = exhibitionItemService.add(EXHIBITION_ID, OWNER,
                    new AddItemRequest(new PlacementRequest(0.25, 0.4, 0.2, 0.3, -37.5),
                            "https://img/x.png", "굿즈", null, null));

            // 위치·크기와 달리 각도는 화면 크기와 무관해서 비율로 바꾸지 않고 그대로 씁니다.
            assertThat(response.rotation()).isEqualTo(-37.5);
        }

        @Test
        void 회전을_보내지_않으면_0으로_저장한다() {
            // 회전 기능이 생기기 전에 만들어진 화면이 그대로 동작해야 합니다.
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.save(any(ExhibitionItem.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ExhibitionItemResponse response = exhibitionItemService.add(EXHIBITION_ID, OWNER,
                    new AddItemRequest(new PlacementRequest(0.25, 0.4, 0.2, 0.3, null),
                            "https://img/x.png", "굿즈", null, null));

            assertThat(response.rotation())
                    .as("null 을 그대로 두면 not null 컬럼에서 저장이 실패합니다")
                    .isEqualTo(0.0);
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
                    new UpdatePositionRequest(new PlacementRequest(0.8, 0.9, 0.15, 0.15, 0.0)));

            assertThat(response.posX()).isEqualTo(0.8);
            assertThat(response.posY()).isEqualTo(0.9);
            assertThat(target.getWidth()).isEqualTo(0.15);
        }

        @Test
        void 회전만_바꾸는_것도_같은_API로_저장된다() {
            // 프론트가 회전 핸들을 놓을 때도 이 API 를 씁니다. 위치를 안 바꿔도 같이 보냅니다.
            ExhibitionItem target = item(5L);
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findById(5L)).willReturn(Optional.of(target));

            ExhibitionItemResponse response = exhibitionItemService.updatePosition(
                    EXHIBITION_ID, 5L, OWNER,
                    new UpdatePositionRequest(new PlacementRequest(0.1, 0.2, 0.3, 0.3, 90.0)));

            assertThat(response.rotation()).isEqualTo(90.0);
            assertThat(target.getRotation()).isEqualTo(90.0);
        }

        @Test
        void 회전을_안_보내면_기존_각도를_지운다_가_아니라_유지한다() {
            // 회전 UI 가 없는 화면은 현재 각도를 몰라서 같이 보낼 수가 없습니다.
            // 그런 화면이 드래그만 해도 사용자가 돌려둔 각도가 사라지면 안 됩니다.
            ExhibitionItem target = item(5L);
            target.moveTo(new ExhibitionItem.Placement(0.1, 0.2, 0.3, 0.3, 45.0));

            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findById(5L)).willReturn(Optional.of(target));

            ExhibitionItemResponse response = exhibitionItemService.updatePosition(
                    EXHIBITION_ID, 5L, OWNER,
                    new UpdatePositionRequest(new PlacementRequest(0.8, 0.9, 0.15, 0.15, null)));

            assertThat(response.rotation())
                    .as("회전을 생략한 요청이 기존 각도를 0 으로 덮으면 안 됩니다")
                    .isEqualTo(45.0);
            assertThat(response.posX())
                    .as("위치·크기는 보낸 값으로 갱신되어야 합니다")
                    .isEqualTo(0.8);
        }

        @Test
        void 회전을_0으로_보내면_실제로_0이_된다() {
            // "생략" 과 "0 으로 지정" 은 다릅니다. 회전을 되돌리는 것도 가능해야 합니다.
            ExhibitionItem target = item(5L);
            target.moveTo(new ExhibitionItem.Placement(0.1, 0.2, 0.3, 0.3, 45.0));

            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findById(5L)).willReturn(Optional.of(target));

            ExhibitionItemResponse response = exhibitionItemService.updatePosition(
                    EXHIBITION_ID, 5L, OWNER,
                    new UpdatePositionRequest(new PlacementRequest(0.1, 0.2, 0.3, 0.3, 0.0)));

            assertThat(response.rotation()).isEqualTo(0.0);
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
                            new UpdatePositionRequest(new PlacementRequest(0.8, 0.9, 0.15, 0.15, 0.0))));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.ITEM_NOT_FOUND);
        }

        @Test
        void 남의_장식장이면_막힌다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, STRANGER))
                    .willThrow(new BusinessException(ExhibitionErrorCode.NOT_EXHIBITION_OWNER));

            assertThrows(BusinessException.class,
                    () -> exhibitionItemService.updatePosition(EXHIBITION_ID, 5L, STRANGER,
                            new UpdatePositionRequest(new PlacementRequest(0.1, 0.1, 0.1, 0.1, 0.0))));

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

        /** 헤더뿐 아니라 바이트까지 검사하므로 진짜 PNG 여야 통과합니다. */
        private org.springframework.mock.web.MockMultipartFile validPng() throws Exception {
            return png(realPngBytes());
        }

        private com.duckspace.domain.exhibition.dto.request.UploadItemRequest uploadRequest() {
            return new com.duckspace.domain.exhibition.dto.request.UploadItemRequest(
                    new PlacementRequest(0.3, 0.4, 0.2, 0.2, 0.0), "치이카와 인형", 15000, "귀여움");
        }

        @Test
        void 접수만_하고_PENDING_으로_응답한다() throws Exception {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.save(any(ExhibitionItem.class)))
                    .willAnswer(inv -> {
                        ExhibitionItem saved = inv.getArgument(0);
                        ReflectionTestUtils.setField(saved, "id", 7L);
                        return saved;
                    });

            ExhibitionItemResponse response =
                    exhibitionItemService.upload(EXHIBITION_ID, OWNER, validPng(), uploadRequest());

            assertThat(response.itemId()).isEqualTo(7L);
            assertThat(response.status()).isEqualTo(ItemStatus.PENDING);
            assertThat(response.imageUrl()).isNull();
            assertThat(response.posX()).isEqualTo(0.3);
        }

        @Test
        void 커밋_이후_처리되도록_이벤트를_발행한다() throws Exception {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.save(any(ExhibitionItem.class)))
                    .willAnswer(inv -> {
                        ExhibitionItem saved = inv.getArgument(0);
                        ReflectionTestUtils.setField(saved, "id", 7L);
                        return saved;
                    });

            exhibitionItemService.upload(EXHIBITION_ID, OWNER, validPng(), uploadRequest());

            org.mockito.ArgumentCaptor<ItemImageUploadedEvent> captor =
                    org.mockito.ArgumentCaptor.forClass(ItemImageUploadedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().itemId()).isEqualTo(7L);
            assertThat(captor.getValue().fileName()).isEqualTo("goods.png");
            assertThat(captor.getValue().exhibitionId())
                    .as("저장 경로를 만들 때 DB를 다시 읽지 않도록 이벤트에 담아 보냅니다")
                    .isEqualTo(EXHIBITION_ID);
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

        @Test
        void 이미지가_아닌_바이트는_헤더를_속여도_거부한다() {
            // Content-Type 은 클라이언트가 보내는 값이라 믿을 수 없습니다.
            // 이걸 막지 않으면 업로드 창구가 그대로 파일 호스팅이 됩니다.
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            var disguised = new org.springframework.mock.web.MockMultipartFile(
                    "image", "goods.png", "image/png", "#!/bin/sh\necho hi".getBytes());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionItemService.upload(EXHIBITION_ID, OWNER, disguised, uploadRequest()));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.UNSUPPORTED_IMAGE_TYPE);
            verify(exhibitionItemRepository, never()).save(any(ExhibitionItem.class));
            verify(eventPublisher, never()).publishEvent(any(ItemImageUploadedEvent.class));
        }

        @Test
        void 진짜_PNG_는_통과한다() throws Exception {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.save(any(ExhibitionItem.class)))
                    .willAnswer(inv -> {
                        ExhibitionItem saved = inv.getArgument(0);
                        ReflectionTestUtils.setField(saved, "id", 7L);
                        return saved;
                    });

            var real = new org.springframework.mock.web.MockMultipartFile(
                    "image", "goods.png", "image/png", realPngBytes());

            assertThat(exhibitionItemService.upload(EXHIBITION_ID, OWNER, real, uploadRequest()).status())
                    .isEqualTo(ItemStatus.PENDING);
        }

        private byte[] realPngBytes() throws Exception {
            var img = new java.awt.image.BufferedImage(20, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            return out.toByteArray();
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
    @DisplayName("get 메서드는")
    class GetItem {

        private ExhibitionItem pendingItem() {
            ExhibitionItem i = new ExhibitionItem(
                    exhibition, new ExhibitionItem.Placement(0.1, 0.2, 0.3, 0.3),
                    "https://img/original.png", "굿즈", null, null, ItemStatus.PENDING);
            ReflectionTestUtils.setField(i, "id", 5L);
            return i;
        }

        @Test
        void 주인은_처리중인_굿즈를_조회할_수_있다() {
            given(exhibitionService.getExhibition(EXHIBITION_ID)).willReturn(exhibition);
            given(exhibitionItemRepository.findById(5L)).willReturn(Optional.of(pendingItem()));

            ExhibitionItemResponse response = exhibitionItemService.get(EXHIBITION_ID, 5L, OWNER);

            assertThat(response.status()).isEqualTo(ItemStatus.PENDING);
        }

        @Test
        void 남에게는_처리중인_굿즈가_보이지_않는다() {
            // 목록은 상태로 걸러내는데 단건 조회만 열려 있으면,
            // 남의 장식장에서 배경이 안 지워진 원본 주소를 그대로 볼 수 있습니다.
            given(exhibitionService.getExhibition(EXHIBITION_ID)).willReturn(exhibition);
            given(exhibitionItemRepository.findById(5L)).willReturn(Optional.of(pendingItem()));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionItemService.get(EXHIBITION_ID, 5L, STRANGER));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.ITEM_NOT_FOUND);
        }

        @Test
        void 남도_완료된_굿즈는_볼_수_있다() {
            given(exhibitionService.getExhibition(EXHIBITION_ID)).willReturn(exhibition);
            given(exhibitionItemRepository.findById(5L)).willReturn(Optional.of(item(5L)));

            ExhibitionItemResponse response = exhibitionItemService.get(EXHIBITION_ID, 5L, STRANGER);

            assertThat(response.status()).isEqualTo(ItemStatus.READY);
        }
    }

    @Nested
    @DisplayName("retry 메서드는")
    class Retry {

        private ExhibitionItem failedItem(String imageUrl) {
            ExhibitionItem i = new ExhibitionItem(
                    exhibition, new ExhibitionItem.Placement(0.1, 0.2, 0.3, 0.3),
                    imageUrl, "굿즈", null, null, ItemStatus.FAILED);
            ReflectionTestUtils.setField(i, "id", 5L);
            return i;
        }

        @Test
        void 원본을_다시_태우도록_이벤트를_발행한다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findOwnedForUpdate(5L, EXHIBITION_ID))
                    .willReturn(Optional.of(failedItem("https://cdn/origin.png")));

            ExhibitionItemResponse response = exhibitionItemService.retry(EXHIBITION_ID, 5L, OWNER);

            assertThat(response.status())
                    .as("업로드와 같게 즉시 PENDING 으로 돌아와야 폴링을 이어갈 수 있습니다")
                    .isEqualTo(ItemStatus.PENDING);

            ArgumentCaptor<ItemImageRetryRequestedEvent> captor =
                    ArgumentCaptor.forClass(ItemImageRetryRequestedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().sourceImageUrl()).isEqualTo("https://cdn/origin.png");
            assertThat(captor.getValue().exhibitionId()).isEqualTo(EXHIBITION_ID);
        }

        @Test
        void 원본_주소를_지우지_않는다() {
            ExhibitionItem target = failedItem("https://cdn/origin.png");
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findOwnedForUpdate(5L, EXHIBITION_ID)).willReturn(Optional.of(target));

            exhibitionItemService.retry(EXHIBITION_ID, 5L, OWNER);

            // 여기서 지우면 재처리가 실패했을 때 다시 시도할 방법이 사라집니다.
            assertThat(target.getImageUrl()).isEqualTo("https://cdn/origin.png");
        }

        @Test
        void 오래_방치된_PENDING은_재시도를_허용한다() {
            // 강제 종료(OOM 등)로 처리가 끊기면 PENDING 이 영원히 남는데,
            // FAILED 만 재시도를 받으면 사용자가 복구할 방법이 없습니다.
            ExhibitionItem stuck = new ExhibitionItem(
                    exhibition, new ExhibitionItem.Placement(0.1, 0.2, 0.3, 0.3),
                    "https://cdn/origin.png", "굿즈", null, null, ItemStatus.PENDING);
            ReflectionTestUtils.setField(stuck, "id", 5L);
            ReflectionTestUtils.setField(stuck, "updatedAt", java.time.LocalDateTime.now().minusMinutes(20));

            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findOwnedForUpdate(5L, EXHIBITION_ID)).willReturn(Optional.of(stuck));

            ExhibitionItemResponse response = exhibitionItemService.retry(EXHIBITION_ID, 5L, OWNER);

            assertThat(response.status()).isEqualTo(ItemStatus.PENDING);
            verify(eventPublisher).publishEvent(any(ItemImageRetryRequestedEvent.class));
            // PENDING→PENDING 은 더티체킹으로는 UPDATE 가 안 나가 방치 시계가 그대로 남습니다.
            // 명시적으로 되감지 않으면 연타마다 재처리가 중복 접수됩니다.
            verify(exhibitionItemRepository).touchUpdatedAt(org.mockito.ArgumentMatchers.eq(5L),
                    any(java.time.LocalDateTime.class));
        }

        @Test
        void 갓_접수된_PENDING은_여전히_거부한다() {
            // 방금 올린 건 아직 처리 중일 뿐입니다. 여기가 뚫리면 재시도 락의 의미가 없어집니다.
            ExhibitionItem processing = new ExhibitionItem(
                    exhibition, new ExhibitionItem.Placement(0.1, 0.2, 0.3, 0.3),
                    "https://cdn/origin.png", "굿즈", null, null, ItemStatus.PENDING);
            ReflectionTestUtils.setField(processing, "id", 5L);
            ReflectionTestUtils.setField(processing, "updatedAt", java.time.LocalDateTime.now());

            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findOwnedForUpdate(5L, EXHIBITION_ID)).willReturn(Optional.of(processing));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionItemService.retry(EXHIBITION_ID, 5L, OWNER));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.ITEM_NOT_RETRYABLE);
        }

        @Test
        void 실패한_굿즈가_아니면_거부한다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findOwnedForUpdate(5L, EXHIBITION_ID)).willReturn(Optional.of(item(5L)));   // READY

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionItemService.retry(EXHIBITION_ID, 5L, OWNER));

            // 처리 중인 것을 또 넣으면 같은 아이템이 두 번 돌아갑니다.
            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.ITEM_NOT_RETRYABLE);
            verify(eventPublisher, never()).publishEvent(any(ItemImageRetryRequestedEvent.class));
        }

        @Test
        void 남겨둔_원본이_없으면_다시_올리라고_알려준다() {
            // 큐가 가득 차 접수 단계에서 실패한 경우 imageUrl 이 비어 있습니다.
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findOwnedForUpdate(5L, EXHIBITION_ID)).willReturn(Optional.of(failedItem(null)));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exhibitionItemService.retry(EXHIBITION_ID, 5L, OWNER));

            assertThat(exception.getErrorCode()).isEqualTo(ExhibitionErrorCode.RETRY_SOURCE_MISSING);
        }

        @Test
        void 남의_장식장이면_막힌다() {
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, STRANGER))
                    .willThrow(new BusinessException(ExhibitionErrorCode.NOT_EXHIBITION_OWNER));

            assertThrows(BusinessException.class,
                    () -> exhibitionItemService.retry(EXHIBITION_ID, 5L, STRANGER));
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
        void 저장된_이미지도_함께_정리한다() {
            ExhibitionItem target = item(5L);
            given(exhibitionService.getOwnedExhibition(EXHIBITION_ID, OWNER)).willReturn(exhibition);
            given(exhibitionItemRepository.findById(5L)).willReturn(Optional.of(target));

            exhibitionItemService.delete(EXHIBITION_ID, 5L, OWNER);

            // 이걸 빼면 DB 행만 사라지고 S3 객체는 영원히 남습니다. 공유 여부 판단은
            // ImageCleanup 이 삭제 직전에 합니다 (ImageCleanupTest 에서 검증).
            verify(imageCleanup).deleteAfterCommit("https://img/a.png");
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
