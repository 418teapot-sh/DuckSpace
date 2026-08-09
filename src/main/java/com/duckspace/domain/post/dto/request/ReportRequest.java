package com.duckspace.domain.post.dto.request;

import com.duckspace.domain.post.entity.Report;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        @Size(max = Report.REASON_MAX_LENGTH) String reason
) {
}
