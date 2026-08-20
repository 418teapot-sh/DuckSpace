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
     * 좋아요가 많은 순으로 장식장 id 를 가져옵니다. <b>굿즈가 하나도 없는 장식장은 뺍니다.</b>
     *
     * <p>엔티티가 아니라 <b>id 만</b> 뽑는 이유는, 엔티티를 그대로 group by 하면 모든 컬럼이
     * group by 절에 들어가야 해서 DB 마다 동작이 갈리기 때문입니다. id 목록을 받아 두 번째 쿼리로
     * 엔티티를 채우는 편이 안전합니다.
     *
     * <p>좋아요가 0개인 장식장도 나오도록 left join 입니다.
     *
     * @see #findRecentIds 빈 장식장을 빼는 이유
     */
    @Query("""
            select e.id from Exhibition e
            left join ExhibitionLike l on l.exhibition = e
            where exists (select 1 from ExhibitionItem i where i.exhibition = e and i.status = :status)
            group by e.id
            order by count(l.id) desc, e.id desc
            """)
    List<Long> findPopularIds(@Param("status") ItemStatus status, Pageable pageable);

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
     * 내 장식장 목록 더보기. 커서(마지막으로 받은 장식장 id)보다 뒤엣것만 가져옵니다.
     *
     * <p>이게 없으면 상한(50)에 걸린 뒤로는 <b>자기 장식장인데도 닿을 방법이 없습니다</b> —
     * 위 메서드 문서가 "이게 없으면 자기 장식장을 다시 찾을 방법이 없다" 고 적어둔 그 경로인데
     * 정작 51번째부터는 그 경로로도 안 나왔습니다.
     */
    @Query("select e.id from Exhibition e where e.userId = :userId and e.id > :cursor order by e.id asc")
    List<Long> findIdsByUserIdAfter(@Param("userId") Long userId,
                                     @Param("cursor") Long cursor,
                                     Pageable pageable);

    /**
     * 검색 탭 기본 화면의 장식장 피드용 — 등록된 순서(최신순)로 장식장 id 를 가져옵니다.
     * {@link #findPopularIds}와 달리 좋아요 수로 정렬하지 않습니다.
     *
     * <h2>굿즈가 하나도 없는 장식장은 뺍니다</h2>
     *
     * <p>가입할 때마다 기본 장식장이 자동으로 생기는데(회원가입 → {@code createDefault}),
     * 여기에 필터가 없으면 <b>첫 페이지가 빈 카드로 도배됩니다.</b> 가입자가 늘수록 심해지고,
     * 굿즈를 실제로 올린 장식장은 뒤로 밀립니다.
     *
     * <p><b>{@code status} 기준입니다</b> — 사진이 아직 처리 중({@code PENDING})이거나
     * 실패({@code FAILED})한 것만 있으면 카드에 그릴 그림이 없으므로 빈 것으로 봅니다.
     * 그래서 사진을 막 올린 직후에는 잠시 피드에 안 보이다가, 처리가 끝나면 나타납니다.
     * 그림 없는 카드를 먼저 보여주는 것보다 낫다고 판단했습니다.
     *
     * <p><b>필터는 반드시 여기(SQL)에 있어야 합니다.</b> 서비스에서 걸러내면 커서 페이징이
     * 깨집니다 — {@code pageSize + 1} 개를 받아 그중 다수가 빠지면 페이지가 요청한 크기보다
     * 짧아지고, {@code hasNext} 는 <b>필터 전</b> 개수로 계산돼 이미 틀린 값이 됩니다.
     * 페이지를 채우려면 루프를 돌며 다시 조회해야 합니다.
     *
     * <p><b>반대로 {@code getMine} · {@code getByUser} · {@code getPrimary} 에는 넣지 않습니다.</b>
     * 자기 장식장 목록에서 빈 것이 사라지면 방금 만든 장식장을 찾을 수 없습니다.
     * 검색({@code searchExhibitionIdsByItem})은 굿즈 이름으로 찾으므로 빈 장식장이 애초에
     * 걸리지 않습니다.
     *
     * <p>{@code cursor} 가 {@code null} 이면 첫 페이지, 아니면 그보다 오래된 것만 가져옵니다.
     * 서비스단에서 {@code cursor} 가 유효한 id(1 이상)일 때만 넘겨줘야 합니다 — 0 이하를
     * 그대로 넘기면 id 가 1부터 시작하는 이 테이블 특성상 {@code e.id < 0} 이 되어 데이터가
     * 있어도 항상 빈 목록이 나옵니다(PR #86 리뷰).
     */
    @Query("""
            select e.id from Exhibition e
            where (:cursor is null or e.id < :cursor)
              and exists (select 1 from ExhibitionItem i where i.exhibition = e and i.status = :status)
            order by e.id desc
            """)
    List<Long> findRecentIds(@Param("cursor") Long cursor,
                             @Param("status") ItemStatus status,
                             Pageable pageable);

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
