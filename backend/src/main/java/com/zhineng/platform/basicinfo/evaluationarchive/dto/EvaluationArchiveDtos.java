package com.zhineng.platform.basicinfo.evaluationarchive.dto;

import java.util.List;
import java.util.Map;

public final class EvaluationArchiveDtos {
    private EvaluationArchiveDtos() {
    }

    public record SaveRequest(
            Long orgUnitId,
            Integer evaluationYear,
            String evaluationType,
            String evaluationGrade,
            String description,
            String accessLevel,
            Integer rowVersion
    ) {
    }

    public record WithdrawRequest(Integer rowVersion, String reason) {
    }

    public record VersionRequest(Integer rowVersion) {
    }

    public record StatusRequest(String status) {
    }

    public record Page(List<Map<String, Object>> items, long total, int page, int size,
                       int totalPages) {
    }

    public record Stats(long total, long drafts, long archived, long complete,
                        long attachments) {
    }

    public record Error(String code, String message) {
    }
}
