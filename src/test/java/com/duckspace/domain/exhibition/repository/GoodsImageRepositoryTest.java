package com.duckspace.domain.exhibition.repository;

import com.duckspace.domain.exhibition.entity.GoodsImage;
import com.duckspace.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 보관함 커서 페이징과 삭제 보호용 URL 조회를 실제 DB 로 확인합니다. */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class GoodsImageRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GoodsImageRepository goodsImageRepository;

    private GoodsImage ready(Long userId, String url) {
        GoodsImage image = new GoodsImage(userId);
        image.markReady(url);
        return entityManager.persist(image);
    }

    @Test
    @DisplayName("내 사진만, 최신순으로, 커서 이전 것만 가져온다")
    void 보관함_커서_페이징() {
        GoodsImage mine1 = ready(1L, "https://cdn/1.png");
        ready(2L, "https://cdn/other.png");
        GoodsImage mine2 = ready(1L, "https://cdn/2.png");
        GoodsImage mine3 = ready(1L, "https://cdn/3.png");
        entityManager.flush();

        List<GoodsImage> first = goodsImageRepository.findByUserIdOrderByIdDesc(1L, PageRequest.of(0, 2));
        assertThat(first).extracting(GoodsImage::getId)
                .containsExactly(mine3.getId(), mine2.getId());

        List<GoodsImage> next = goodsImageRepository.findByUserIdAndIdLessThanOrderByIdDesc(
                1L, mine2.getId(), PageRequest.of(0, 2));
        assertThat(next).extracting(GoodsImage::getId).containsExactly(mine1.getId());
    }

    @Test
    @DisplayName("삭제 보호 — 보관함이 소유한 URL 을 골라낸다")
    void 소유_URL_조회() {
        ready(1L, "https://cdn/lib.png");
        entityManager.flush();

        assertThat(goodsImageRepository.existsByImageUrl("https://cdn/lib.png")).isTrue();
        assertThat(goodsImageRepository.existsByImageUrl("https://cdn/none.png")).isFalse();
    }
}
