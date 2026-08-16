package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 같은 아이템에 처리 작업이 둘 이상 떠 있을 수 있습니다(빠른 재시도 등).
 * <b>늦게 끝난 쪽이 먼저 끝난 쪽의 결과를 덮으면 안 됩니다.</b>
 */
@ExtendWith(MockitoExtension.class)
class ExhibitionItemStatusWriterTest {

    private static final Long ITEM_ID = 5L;

    @Mock
    private ExhibitionItemRepository exhibitionItemRepository;

    @InjectMocks
    private ExhibitionItemStatusWriter statusWriter;

    private Exhibition exhibition;

    @BeforeEach
    void setUp() {
        exhibition = new Exhibition(1L, "장식장", null);
        ReflectionTestUtils.setField(exhibition, "id", 10L);
    }

    private ExhibitionItem itemWith(ItemStatus status, String imageUrl) {
        ExhibitionItem item = new ExhibitionItem(
                exhibition, new ExhibitionItem.Placement(0.1, 0.2, 0.3, 0.3),
                imageUrl, "굿즈", null, null, status);
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        return item;
    }

    @Test
    @DisplayName("PENDING 이면 결과를 기록한다")
    void 처리중이면_기록한다() {
        ExhibitionItem item = itemWith(ItemStatus.PENDING, null);
        given(exhibitionItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

        boolean written = statusWriter.markReady(ITEM_ID, "https://cdn/result.png");

        assertThat(written).isTrue();
        assertThat(item.getStatus()).isEqualTo(ItemStatus.READY);
        assertThat(item.getImageUrl()).isEqualTo("https://cdn/result.png");
    }

    @Test
    @DisplayName("이미 READY 면 뒤늦은 실패 결과가 덮어쓰지 못한다")
    void 늦게_끝난_실패가_성공을_덮지_못한다() {
        // 재시도가 두 번 접수돼 A 가 먼저 성공(READY + 원본 삭제)한 뒤,
        // B 가 사라진 원본을 못 찾아 실패로 돌아온 상황입니다.
        ExhibitionItem item = itemWith(ItemStatus.READY, "https://cdn/result.png");
        given(exhibitionItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

        boolean written = statusWriter.markFailed(ITEM_ID, "https://cdn/deleted-origin.png");

        assertThat(written)
                .as("기록하지 못했다는 신호가 있어야 호출부가 방금 올린 객체를 회수할 수 있습니다")
                .isFalse();
        assertThat(item.getStatus())
                .as("이미 화면에 잘 뜨는 굿즈가 FAILED 로 되돌아가면 안 됩니다")
                .isEqualTo(ItemStatus.READY);
        assertThat(item.getImageUrl())
                .as("이미 지워진 원본 주소를 가리키게 되면 그림이 깨집니다")
                .isEqualTo("https://cdn/result.png");
    }

    @Test
    @DisplayName("이미 FAILED 면 뒤늦은 성공 결과도 무시한다")
    void 늦게_끝난_성공도_무시한다() {
        ExhibitionItem item = itemWith(ItemStatus.FAILED, "https://cdn/origin.png");
        given(exhibitionItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

        assertThat(statusWriter.markReady(ITEM_ID, "https://cdn/late.png")).isFalse();
        assertThat(item.getStatus()).isEqualTo(ItemStatus.FAILED);
    }

    @Test
    @DisplayName("처리 중에 삭제된 아이템이면 조용히 넘어간다")
    void 아이템이_없으면_아무것도_하지_않는다() {
        given(exhibitionItemRepository.findById(ITEM_ID)).willReturn(Optional.empty());

        assertThat(statusWriter.markReady(ITEM_ID, "https://cdn/result.png")).isFalse();   // 예외 없음
    }
}
