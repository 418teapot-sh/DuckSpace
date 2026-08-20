package com.duckspace.domain.exhibition.image;

import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import com.duckspace.domain.exhibition.repository.GoodsImageRepository;
import com.duckspace.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Mock
    private UserRepository userRepository;

    private ImageCleanup imageCleanup;

    @BeforeEach
    void setUp() {
        // 테스트에서는 큐 없이 같은 스레드에서 바로 실행합니다.
        imageCleanup = new ImageCleanup(imageStorage, exhibitionItemRepository,
                goodsImageRepository, userRepository, Runnable::run);
    }

    @Test
    @DisplayName("프로필 사진으로 쓰이는 URL 은 지우지 않는다")
    void 프로필_사진이면_보존() {
        // 프론트가 프로필 사진을 게시글 이미지 업로드로 올려서, 여기서 안 보면
        // "글에 안 쓰인 이미지" 로 잡혀 24시간 뒤 파일만 사라집니다(깨진 아바타).
        given(exhibitionItemRepository.findReferencedUrls(List.of(URL))).willReturn(List.of());
        given(goodsImageRepository.findReferencedUrls(List.of(URL))).willReturn(List.of());
        given(userRepository.findProfileImageUrlsIn(List.of(URL))).willReturn(List.of(URL));

        imageCleanup.deleteAfterCommit(URL);

        verify(imageStorage, never()).deleteByUrl(URL);
    }

    @Test
    @DisplayName("굿즈가 아직 배치 중인 URL 은 지우지 않는다")
    void 굿즈가_참조_중이면_보존() {
        given(exhibitionItemRepository.findReferencedUrls(List.of(URL))).willReturn(List.of(URL));
        given(goodsImageRepository.findReferencedUrls(List.of(URL))).willReturn(List.of());
        given(userRepository.findProfileImageUrlsIn(List.of(URL))).willReturn(List.of());

        imageCleanup.deleteAfterCommit(URL);

        verify(imageStorage, never()).deleteByUrl(URL);
    }

    @Test
    @DisplayName("보관함이 소유한 URL 은 지우지 않는다")
    void 보관함이_소유하면_보존() {
        given(exhibitionItemRepository.findReferencedUrls(List.of(URL))).willReturn(List.of());
        given(goodsImageRepository.findReferencedUrls(List.of(URL))).willReturn(List.of(URL));
        given(userRepository.findProfileImageUrlsIn(List.of(URL))).willReturn(List.of());

        imageCleanup.deleteAfterCommit(URL);

        verify(imageStorage, never()).deleteByUrl(URL);
    }

    @Test
    @DisplayName("아무도 참조하지 않는 URL 만 실제로 지운다")
    void 미참조면_삭제() {
        given(exhibitionItemRepository.findReferencedUrls(List.of(URL))).willReturn(List.of());
        given(goodsImageRepository.findReferencedUrls(List.of(URL))).willReturn(List.of());
        given(userRepository.findProfileImageUrlsIn(List.of(URL))).willReturn(List.of());

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
        given(userRepository.findProfileImageUrlsIn(List.of(URL))).willReturn(List.of());

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
    @DisplayName("참조 확인이 일시 오류면 배치를 다시 시도해 끝내 지운다")
    void 참조_확인_일시_오류는_재시도() {
        // 커넥션 풀 고갈 같은 일시 오류 한 번에 배치 전체가 조용히 사라지면
        // 그 파일들은 회수 기회 없이 영구 누수가 됩니다.
        given(exhibitionItemRepository.findReferencedUrls(List.of(URL)))
                .willThrow(new RuntimeException("pool exhausted"))
                .willReturn(List.of());
        given(goodsImageRepository.findReferencedUrls(List.of(URL))).willReturn(List.of());
        given(userRepository.findProfileImageUrlsIn(List.of(URL))).willReturn(List.of());

        imageCleanup.deleteAfterCommit(URL);

        verify(imageStorage).deleteByUrl(URL);
    }

    @Test
    @DisplayName("참조 확인이 계속 실패하면 지우지 않고 물러난다")
    void 참조_확인_계속_실패면_삭제하지_않는다() {
        // 참조를 모르는 채로 지우는 쪽이 더 위험합니다. 남기고 에러 로그(수동 회수용)로 끝냅니다.
        given(exhibitionItemRepository.findReferencedUrls(List.of(URL)))
                .willThrow(new RuntimeException("db down"));

        imageCleanup.deleteAfterCommit(URL);   // 예외가 밖으로 새지 않아야 합니다.

        verify(imageStorage, never()).deleteByUrl(URL);
    }

    @Test
    @DisplayName("같은 URL 이 여러 번 들어와도 저장소 삭제는 한 번만 부른다")
    void 중복_URL_은_한_번만_지운다() {
        // 같은 사진을 한 장식장에 여러 번 놓는 게 정식 기능이라, 그 장식장을 지우면
        // 같은 URL 이 배치 수만큼 들어옵니다.
        List<String> withDuplicates = List.of(URL, URL, URL);
        given(exhibitionItemRepository.findReferencedUrls(List.of(URL))).willReturn(List.of());
        given(goodsImageRepository.findReferencedUrls(List.of(URL))).willReturn(List.of());
        given(userRepository.findProfileImageUrlsIn(List.of(URL))).willReturn(List.of());

        imageCleanup.deleteAfterCommit(withDuplicates);

        verify(imageStorage, times(1)).deleteByUrl(URL);
    }

    @Test
    @DisplayName("URL 이 많으면 참조 확인 쿼리를 나눠서 보낸다")
    void 많은_URL_은_나눠서_확인한다() {
        // 상한이 없으면 굿즈 수만큼의 in 절이 한 번에 나갑니다. 파싱 비용도 크고
        // 파라미터 수가 매번 달라져 실행 계획 캐시도 흔들립니다.
        List<String> many = IntStream.range(0, 450)
                .mapToObj(i -> "https://cdn/goods-%d.png".formatted(i))
                .toList();
        given(exhibitionItemRepository.findReferencedUrls(anyList())).willReturn(List.of());
        given(goodsImageRepository.findReferencedUrls(anyList())).willReturn(List.of());

        imageCleanup.deleteAfterCommit(many);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(exhibitionItemRepository, times(3)).findReferencedUrls(captor.capture());
        assertThat(captor.getAllValues()).extracting(List::size).containsExactly(200, 200, 50);
        verify(imageStorage, times(450)).deleteByUrl(anyString());
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
