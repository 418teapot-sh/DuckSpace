package com.duckspace.domain.post.repository;

/** 게시글 id별 집계(좋아요 수/댓글 수 등) 배치 조회용 공용 프로젝션. */
public interface PostIdCount {

    Long getPostId();

    long getCount();
}
