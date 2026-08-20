package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.BoardType;
import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.entity.PostImage;
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
import static org.assertj.core.api.Assertions.tuple;

/**
 * search()의 JPQL where 절(특히 and/or 괄호 묶음)은 목(mock)으로는 검증되지 않습니다.
 * 실제 DB로 authorId 필터와 keyword 필터가 서로 AND로 묶이는지 확인합니다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class PostRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostImageRepository postImageRepository;

    private Post casual(Long userId, String content) {
        return entityManager.persist(Post.createCasual(userId, content));
    }

    private void image(Post post, String url, int sortOrder) {
        entityManager.persist(new PostImage(post, url, sortOrder));
    }

    @Test
    @DisplayName("authorId가 없으면 작성자와 무관하게 전부 내려온다")
    void authorId가_없으면_전체_조회() {
        casual(1L, "글1");
        casual(2L, "글2");
        entityManager.flush();

        List<Post> result = postRepository.search(BoardType.CASUAL, null, null, null, PageRequest.of(0, 20));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("authorId가 있으면 그 작성자가 쓴 글만 내려온다")
    void authorId로_작성자를_좁힌다() {
        Post mine = casual(1L, "내 글");
        casual(2L, "남의 글");
        entityManager.flush();

        List<Post> result = postRepository.search(BoardType.CASUAL, null, null, 1L, PageRequest.of(0, 20));

        assertThat(result).extracting(Post::getId).containsExactly(mine.getId());
    }

    @Test
    @DisplayName("authorId와 keyword를 같이 주면 AND로 좁혀진다")
    void authorId와_keyword를_함께_주면_AND_조건() {
        casual(1L, "치이카와 교환합니다");
        casual(1L, "그냥 잡담");
        casual(2L, "치이카와 교환합니다");
        entityManager.flush();

        List<Post> result = postRepository.search(BoardType.CASUAL, null, "치이카와", 1L, PageRequest.of(0, 20));

        assertThat(result).extracting(Post::getContent).containsExactly("치이카와 교환합니다");
    }

    @Test
    @DisplayName("다른 작성자의 글이 있어도 authorId로 좁히면 그 사람 글만 keyword와 무관하게 걸러진다")
    void authorId가_다르면_keyword가_맞아도_제외된다() {
        casual(2L, "치이카와 교환합니다");
        entityManager.flush();

        List<Post> result = postRepository.search(BoardType.CASUAL, null, "치이카와", 1L, PageRequest.of(0, 20));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("대표 이미지 배치 조회 — 글마다 sortOrder 0 한 장만, 여러 글을 한 번에")
    void 대표_이미지_배치조회() {
        // 목록 카드는 이미지를 한 장만 그립니다. 잡담 글은 최대 4장까지 붙을 수 있어서
        // 전부 가져오면 쓰지도 않는 URL 이 응답을 네 배로 불립니다.
        Post first = casual(1L, "사진 세 장");
        image(first, "https://img/a-0.png", 0);
        image(first, "https://img/a-1.png", 1);
        image(first, "https://img/a-2.png", 2);

        Post second = casual(2L, "사진 한 장");
        image(second, "https://img/b-0.png", 0);

        Post noImage = casual(3L, "사진 없음");
        entityManager.flush();
        entityManager.clear();

        List<PostThumbnail> thumbnails = postImageRepository.findThumbnails(
                List.of(first.getId(), second.getId(), noImage.getId()),
                PostImage.THUMBNAIL_SORT_ORDER);

        assertThat(thumbnails)
                .as("사진 있는 글 두 개만, 각각 0번 한 장씩")
                .extracting(PostThumbnail::getPostId, PostThumbnail::getImageUrl)
                .containsExactlyInAnyOrder(
                        tuple(first.getId(), "https://img/a-0.png"),
                        tuple(second.getId(), "https://img/b-0.png"));

        assertThat(thumbnails)
                .as("사진 없는 글은 결과에 아예 없어야 서비스에서 thumbnailUrl 이 null 이 됩니다")
                .extracting(PostThumbnail::getPostId)
                .doesNotContain(noImage.getId());
    }
}
