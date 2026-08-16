package com.duckspace.domain.user.repository;

import com.duckspace.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
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
}
