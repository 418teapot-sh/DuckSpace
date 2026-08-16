package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.BoardType;
import com.duckspace.domain.post.entity.Post;
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

    private Post casual(Long userId, String content) {
        return entityManager.persist(Post.createCasual(userId, content));
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
}
