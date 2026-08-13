package com.duckspace.domain.exhibition.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** 드래그 이동·크기 조절 결과를 저장하는 요청. */
public record UpdatePositionRequest(
        @NotNull @Valid PlacementRequest placement
) {
}
