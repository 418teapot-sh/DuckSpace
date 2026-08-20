package com.duckspace.domain.post.repository;

/**
 * 목록 카드에 쓸 게시글 대표 이미지 배치 조회용 프로젝션.
 *
 * <p>엔티티({@code PostImage}) 대신 필요한 두 값만 뽑습니다. 엔티티로 받으면 목록 크기만큼
 * 영속성 컨텍스트에 올라가고, {@code image.getPost().getId()} 로 글 id 를 꺼내야 해서
 * 프록시 동작에 기대게 됩니다. 여기서는 {@code post_id} 를 그대로 읽습니다.
 *
 * @see PostIdCount 같은 목적의 집계용 프로젝션
 */
public interface PostThumbnail {

    Long getPostId();

    String getImageUrl();
}
