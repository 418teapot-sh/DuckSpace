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
     * 내 장식장 id 목록. 만든 순서대로(오래된 것부터)입니다.
     *
     * <p>이게 없으면 <b>사용자가 자기 장식장을 다시 찾을 방법이 없습니다.</b> 조회 경로가
     * 인기순·검색·id 직접 지정뿐이라, 좋아요를 못 받은 장식장은 만들고 나면 사라집니다.
     *
     * <p>id 만 뽑는 이유는 {@link #findPopularIds} 와 같습니다 — 뒤이어 대표이미지·좋아요를
     * 한 번에 채우는 공통 경로를 태우기 위해서입니다.
     */
    @Query("select e.id from Exhibition e where e.userId = :userId order by e.id asc")
    List<Long> findIdsByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * 검색 탭 기본 화면의 장식장 피드용 — 필터 없이 등록된 순서(최신순)로 전체 장식장 id를 가져옵니다.
     * {@link #findPopularIds}와 달리 좋아요 수로 정렬하지 않습니다.
     */
    @Query("select e.id from Exhibition e order by e.id desc")
    List<Long> findRecentIds(Pageable pageable);

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
