package com.duckspace.domain.post.dto.response;

import com.duckspace.domain.post.entity.BoardType;
import com.duckspace.domain.post.entity.ExchangeStatus;
import com.duckspace.domain.post.entity.ItemCondition;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글 상세. 잡담/교환 공용 응답이라 보드 타입별 섹션은 해당 없으면 null(또는 빈 리스트)입니다.
 * 잡담: imageUrls/hashtags만 채워지고 exchangeInfo는 null.
 * 교환: exchangeInfo만 채워지고 imageUrls/hashtags는 빈 리스트.
 */
public record PostDetailResponse(
        Long id,
        BoardType boardType,
        String title,
        String content,
        Long authorId,
        String authorNickname,
        LocalDateTime createdAt,
        boolean mine,
        long likeCount,
        boolean liked,
        long commentCount,
        List<String> imageUrls,
        List<String> hashtags,
        ExchangeInfo exchangeInfo
) {
    public record ExchangeInfo(
            ExchangeStatus status,
            String extraCondition,
            String preferredPopupName,
            String preferredDate,
            String preferredTime,
            TradeItemInfo offeredItem,
            TradeItemInfo wantedItem
    ) {
    }

    public record TradeItemInfo(
            String imageUrl,
            String itemName,
            String brand,
            ItemCondition condition
    ) {
    }
}
