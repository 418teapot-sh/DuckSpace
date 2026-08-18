package com.duckspace.domain.exhibition.image;

import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import com.duckspace.domain.exhibition.repository.GoodsImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 공유 이미지 삭제 보호의 <b>최종 방어선</b>을 검증합니다.
 *
 * <p>호출부가 아니라 여기(삭제 직전)서 참조를 확인하는 이유: 판단을 호출부마다 두면
 * 빼먹는 경로가 생기고(실제로 재시도 경로에서 빠졌었습니다), 판단과 삭제 사이에
 * 새 배치가 끼어드는 경합도 남기 때문입니다.
 */
@ExtendWith(MockitoExtension.class)
class ImageCleanupTest {

    private static final String URL = "https://cdn/shared.png";

    @Mock
    private ImageStorage imageStorage;

    @Mock
    private ExhibitionItemRepository exhibitionItemRepository;

    @Mock
    private GoodsImageRepository goodsImageRepository;

    private ImageCleanup imageCleanup;

    @BeforeEach
    void setUp() {
        // 테스트에서는 큐 없이 같은 스레드에서 바로 실행합니다.
        imageCleanup = new ImageCleanup(
                imageStorage, exhibitionItemRepository, goodsImageRepository, Runnable::run);
    }

    @Test
    @DisplayName("굿즈가 아직 배치 중인 URL 은 지우지 않는다")
    void 굿즈가_참조_중이면_보존() {
        given(exhibitionItemRepository.existsByImageUrl(URL)).willReturn(true);

        imageCleanup.deleteAfterCommit(URL);

        verify(imageStorage, never()).deleteByUrl(URL);
    }

    @Test
    @DisplayName("보관함이 소유한 URL 은 지우지 않는다")
    void 보관함이_소유하면_보존() {
        given(exhibitionItemRepository.existsByImageUrl(URL)).willReturn(false);
        given(goodsImageRepository.existsByImageUrl(URL)).willReturn(true);

        imageCleanup.deleteAfterCommit(URL);

        verify(imageStorage, never()).deleteByUrl(URL);
    }

    @Test
    @DisplayName("아무도 참조하지 않는 URL 만 실제로 지운다")
    void 미참조면_삭제() {
        given(exhibitionItemRepository.existsByImageUrl(URL)).willReturn(false);
        given(goodsImageRepository.existsByImageUrl(URL)).willReturn(false);

        imageCleanup.deleteAfterCommit(URL);

        verify(imageStorage).deleteByUrl(URL);
    }

    @Test
    @DisplayName("즉시 삭제 경로(재시도 성공 후 원본 정리)도 같은 가드를 탄다")
    void 즉시_삭제도_가드() {
        // 리뷰에서 잡힌 Critical: FAILED 원본 URL 을 이미 배치해 둔 상태에서 재시도가
        // 성공하면 원본이 무조건 지워져 배치된 굿즈가 깨졌습니다. 이제 여기서 막습니다.
        given(exhibitionItemRepository.existsByImageUrl(URL)).willReturn(true);

        imageCleanup.delete(URL);

        verify(imageStorage, never()).deleteByUrl(URL);
    }

    @Test
    @DisplayName("목록 삭제는 URL 별로 따로 판단한다")
    void 목록은_URL_별_판단() {
        given(exhibitionItemRepository.existsByImageUrl("https://cdn/keep.png")).willReturn(true);
        given(exhibitionItemRepository.existsByImageUrl("https://cdn/gone.png")).willReturn(false);
        given(goodsImageRepository.existsByImageUrl("https://cdn/gone.png")).willReturn(false);

        imageCleanup.deleteAfterCommit(List.of("https://cdn/keep.png", "https://cdn/gone.png"));

        verify(imageStorage, never()).deleteByUrl("https://cdn/keep.png");
        verify(imageStorage).deleteByUrl("https://cdn/gone.png");
    }
}
