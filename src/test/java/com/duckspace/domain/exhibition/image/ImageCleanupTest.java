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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 공유 이미지 삭제 보호의 <b>최종 방어선</b>을 검증합니다.
 *
 * <p>판단을 호출부가 아니라 여기(삭제 직전, 전용 실행기 안)서 하는 이유: 호출부마다 두면
 * 빼먹는 경로가 생기고(실제로 재시도 경로에서 빠졌었습니다), 판단과 삭제 사이의
 * 경합 창도 넓어지기 때문입니다.
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
        given(exhibitionItemRepository.findReferencedUrls(List.of(URL))).willReturn(List.of(URL));
        given(goodsImageRepository.findReferencedUrls(List.of(URL))).willReturn(List.of());

        imageCleanup.deleteAfterCommit(URL);

        verify(imageStorage, never()).deleteByUrl(URL);
    }

    @Test
    @DisplayName("보관함이 소유한 URL 은 지우지 않는다")
    void 보관함이_소유하면_보존() {
        given(exhibitionItemRepository.findReferencedUrls(List.of(URL))).willReturn(List.of());
        given(goodsImageRepository.findReferencedUrls(List.of(URL))).willReturn(List.of(URL));

        imageCleanup.deleteAfterCommit(URL);

        verify(imageStorage, never()).deleteByUrl(URL);
    }

    @Test
    @DisplayName("아무도 참조하지 않는 URL 만 실제로 지운다")
    void 미참조면_삭제() {
        given(exhibitionItemRepository.findReferencedUrls(List.of(URL))).willReturn(List.of());
        given(goodsImageRepository.findReferencedUrls(List.of(URL))).willReturn(List.of());

        imageCleanup.deleteAfterCommit(URL);

        verify(imageStorage).deleteByUrl(URL);
    }

    @Test
    @DisplayName("즉시 삭제 경로(재시도 성공 후 원본 정리)도 같은 가드를 탄다")
    void 즉시_삭제도_가드() {
        // 1차 리뷰의 Critical: FAILED 원본 URL 을 이미 배치해 둔 상태에서 재시도가
        // 성공하면 원본이 무조건 지워져 배치된 굿즈가 깨졌습니다. 이제 여기서 막습니다.
        given(exhibitionItemRepository.findReferencedUrls(List.of(URL))).willReturn(List.of(URL));
        given(goodsImageRepository.findReferencedUrls(List.of(URL))).willReturn(List.of());

        imageCleanup.delete(URL);

        verify(imageStorage, never()).deleteByUrl(URL);
    }

    @Test
    @DisplayName("고아 회수(deleteOrphan)는 참조 확인 쿼리 없이 바로 지운다")
    void 고아_회수는_쿼리_없이_삭제() {
        // 방금 이 흐름이 만든 URL 은 DB 에 존재할 수 없어서, 확인 쿼리는 항상
        // false 를 돌려주는 낭비입니다. (2차 리뷰의 효율 지적)
        imageCleanup.deleteOrphan(URL);

        verify(imageStorage).deleteByUrl(URL);
        verify(exhibitionItemRepository, never()).findReferencedUrls(any());
        verify(goodsImageRepository, never()).findReferencedUrls(any());
    }

    @Test
    @DisplayName("목록 삭제는 URL 마다 exists 를 날리지 않고 배치 쿼리 두 번으로 거른다")
    void 목록은_배치_쿼리로_판단() {
        List<String> urls = List.of("https://cdn/keep.png", "https://cdn/gone.png");
        given(exhibitionItemRepository.findReferencedUrls(urls)).willReturn(List.of("https://cdn/keep.png"));
        given(goodsImageRepository.findReferencedUrls(urls)).willReturn(List.of());

        imageCleanup.deleteAfterCommit(urls);

        verify(imageStorage, never()).deleteByUrl("https://cdn/keep.png");
        verify(imageStorage).deleteByUrl("https://cdn/gone.png");
        // URL 수와 무관하게 참조 확인은 저장소당 한 번씩입니다.
        verify(exhibitionItemRepository).findReferencedUrls(urls);
        verify(goodsImageRepository).findReferencedUrls(urls);
    }
}
