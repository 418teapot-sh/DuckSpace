package com.duckspace.domain.post.dto.request;

import com.duckspace.domain.post.entity.ExchangeDetail;
import com.duckspace.domain.post.entity.ExchangeMethod;
import com.duckspace.domain.post.entity.Post;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 교환 글 작성 요청. 프론트의 3단계 wizard를 한 번에 제출받습니다. */
public record ExchangePostRequest(
        @NotNull ExchangeMethod method,
        @NotBlank @Size(max = Post.TITLE_MAX_LENGTH) String title,
        @Size(max = Post.EXCHANGE_CONTENT_MAX_LENGTH) String content,
        @NotNull @Valid OfferedItemRequest offeredItem,
        @NotNull @Valid WantedItemRequest wantedItem,
        @Size(max = ExchangeDetail.EXTRA_CONDITION_MAX_LENGTH) String extraCondition
) {
}
