package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.BoardType;
import com.duckspace.domain.post.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** 삭제된 글은 findById로도 보이면 안 되므로, 조회 경로는 전부 이걸 쓰세요. */
    Optional<Post> findByIdAndDeletedAtIsNull(Long id);

    /** 여러 건 배치 조회용. findAllById를 직접 쓰면 소프트 삭제된 글까지 섞여 나오니 이걸 쓰세요. */
    List<Post> findByIdInAndDeletedAtIsNull(List<Long> ids);

    /**
     * 최신순(id desc) 커서 페이지네이션 + 키워드 검색(제목/본문 부분일치) + 작성자 필터.
     * cursor가 null이면 최신 글부터, 값이 있으면 그보다 id가 작은 글부터 내려줍니다.
     * keyword는 호출 전에 LIKE 와일드카드(%, _, \)가 이스케이프되어 있어야 합니다.
     * authorId가 null이면 전체 작성자 대상, 값이 있으면 그 작성자가 쓴 글만 내려줍니다.
     */
    @Query("""
            select p from Post p
            where p.boardType = :boardType
              and p.deletedAt is null
              and (:cursor is null or p.id < :cursor)
              and (:authorId is null or p.userId = :authorId)
              and (:keyword is null
                   or p.content like concat('%', :keyword, '%') escape '\\'
                   or p.title like concat('%', :keyword, '%') escape '\\')
            order by p.id desc
            """)
    // cursor/authorId는 둘 다 Long이라 자리가 붙어있으면 실수로 바뀌어도 컴파일러가 못 잡습니다.
    // keyword(String)를 사이에 둬서 떨어뜨려 놓습니다.
    List<Post> search(@Param("boardType") BoardType boardType,
                       @Param("cursor") Long cursor,
                       @Param("keyword") String keyword,
                       @Param("authorId") Long authorId,
                       Pageable pageable);
}
