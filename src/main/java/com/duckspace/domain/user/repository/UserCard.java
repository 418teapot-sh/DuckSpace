package com.duckspace.domain.user.repository;

/**
 * 목록 화면에서 <b>작성자 표시에 필요한 것만</b> 뽑는 배치 조회용 프로젝션.
 *
 * <p>{@link UserRepository#findNicknamesByIds} 는 {@code findAllById} 로 {@code User} 엔티티를
 * 통째로 불러온 뒤 닉네임만 꺼내 씁니다. 그래서 {@code profileImageUrl} 이 이미 메모리에 올라와
 * 있는데도 버려졌고, 프론트가 <b>카드마다 같은 유저를 다시 조회</b>하고 있었습니다.
 *
 * <p>여기서는 두 값을 함께 돌려줍니다. 비밀번호 해시 같은 나머지 컬럼은 애초에 읽지 않습니다.
 */
public interface UserCard {

    Long getId();

    String getNickname();

    String getProfileImageUrl();
}
