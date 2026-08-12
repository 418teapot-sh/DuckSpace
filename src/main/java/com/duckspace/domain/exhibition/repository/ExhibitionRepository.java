package com.duckspace.domain.exhibition.repository;

import com.duckspace.domain.exhibition.entity.Exhibition;
import com.duckspace.domain.exhibition.entity.ItemStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExhibitionRepository extends JpaRepository<Exhibition, Long> {

    /**
     * 좋아요가 많은 순으로 장식장 id 를 가져옵니다.
     *
     * <p>엔티티가 아니라 <b>id 만</b> 뽑는 이유는, 엔티티를 그대로 group by 하면 모든 컬럼이
     * group by 절에 들어가야 해서 DB 마다 동작이 갈리기 때문입니다. id 목록을 받아 두 번째 쿼리로
     * 엔티티를 채우는 편이 안전합니다.
     *
     * <p>좋아요가 0개인 장식장도 나오도록 left join 입니다.
     */
    @Query("""
            select e.id from Exhibition e
            left join ExhibitionLike l on l.exhibition = e
            group by e.id
            order by count(l.id) desc, e.id desc
            """)
    List<Long> findPopularIds(Pageable pageable);

    /**
     * 굿즈 이름으로 장식장을 찾습니다. 매칭된 굿즈가 놓인 장식장을 결과로 돌려줍니다.
     *
     * <p>{@code escape '\\'} 는 사용자가 입력한 {@code %} · {@code _} 가 와일드카드로 동작하지
     * 않도록 하기 위한 것입니다. 키워드는 서비스에서 이스케이프해서 넘깁니다.
     */
    @Query("""
            select distinct i.exhibition.id from ExhibitionItem i
            where i.status = :status
              and lower(i.itemName) like lower(concat('%', :keyword, '%')) escape '\\'
            order by i.exhibition.id desc
            """)
    List<Long> searchExhibitionIdsByItem(@Param("keyword") String keyword,
                                          @Param("status") ItemStatus status,
                                          Pageable pageable);
}
