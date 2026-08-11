package com.duckspace.domain.exhibition.dto.response;

import com.duckspace.domain.exhibition.entity.Exhibition;
import org.springframework.data.domain.Page;

import java.util.List;

/** 전시 목록 전체보기 페이지네이션용 */
public record ExhibitionListResponse(
        List<ExhibitionSummaryResponse> exhibitions,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static ExhibitionListResponse from(Page<Exhibition> page) {
        return new ExhibitionListResponse(
                page.getContent().stream()
                        .map(ExhibitionSummaryResponse::from)
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}