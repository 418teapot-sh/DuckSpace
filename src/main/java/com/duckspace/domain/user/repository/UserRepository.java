package com.duckspace.domain.user.repository;

import com.duckspace.domain.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** userId -> nickname 배치 조회. 여러 도메인에서 작성자/신청자 닉네임을 붙일 때 재사용하세요. */
    default Map<Long, String> findNicknamesByIds(Collection<Long> userIds) {
        return findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
    }

    /**
     * userId -> 작성자 표시 정보(닉네임 + 프로필 사진) 배치 조회.
     *
     * <p>닉네임만 필요하면 {@link #findNicknamesByIds} 로 충분하지만, <b>목록 카드에 프로필
     * 사진까지 그리는 화면</b>은 이걸 쓰세요. 위 메서드만 쓰면 프로필 URL 이 빠져서, 클라이언트가
     * 카드마다 유저를 다시 조회하게 됩니다.
     */
    default Map<Long, UserCard> findCardsByIds(Collection<Long> userIds) {
        return findCardsIn(userIds).stream()
                .collect(Collectors.toMap(UserCard::getId, card -> card));
    }

    @Query("""
            select u.id as id, u.nickname as nickname, u.profileImageUrl as profileImageUrl
            from User u where u.id in :userIds
            """)
    List<UserCard> findCardsIn(@Param("userIds") Collection<Long> userIds);

    /**
     * 닉네임으로 유저를 찾습니다. {@code escape '\\'} 는 사용자가 입력한 {@code %} · {@code _} 가
     * 와일드카드로 동작하지 않도록 하기 위한 것입니다. 키워드는 서비스에서 이스케이프해서 넘깁니다.
     */
    @Query("""
            select u from User u
            where lower(u.nickname) like lower(concat('%', :keyword, '%')) escape '\\'
            order by u.id desc
            """)
    List<User> searchByNickname(@Param("keyword") String keyword, Pageable pageable);
}
