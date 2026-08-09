package com.duckspace.domain.post.repository;

import com.duckspace.domain.post.entity.TradeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeItemRepository extends JpaRepository<TradeItem, Long> {

    List<TradeItem> findByExchangeDetail_PostIdOrderBySideAsc(Long postId);

    List<TradeItem> findByExchangeDetail_PostIdIn(List<Long> postIds);

    void deleteByExchangeDetail_PostId(Long postId);
}
