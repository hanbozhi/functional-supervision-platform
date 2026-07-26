package com.zhineng.platform.basicinfo.corefunction.dto;

import java.util.List;
import java.util.Map;

public final class CoreFunctionDtos {
    private CoreFunctionDtos() {
    }

    public record FunctionRequest(
            Long orgUnitId, String functionCode, String functionName, String industryTag,
            String description, Integer sortOrder, Integer versionNo
    ) {
    }

    public record StatusRequest(String status, Integer versionNo) {
    }

    public record DutyRequest(
            Long coreFunctionId, String dutyContent, String keywords,
            Integer sortOrder, Integer versionNo
    ) {
    }

    public record DutyCandidate(
            Long coreFunctionId, String dutyContent, String keywords,
            String sourceSnippet, Integer sortOrder
    ) {
    }

    public record DutyImportRequest(Long sourceVersionId, List<DutyCandidate> items) {
    }

    public record MappingRequest(List<String> departmentNames) {
    }

    public record MatchRequest(Integer threshold) {
    }

    public record ReviewRequest(
            String resultType, Long dutyItemId, Long rightsItemId,
            Double finalScore, String reviewStatus, String opinion, Integer versionNo
    ) {
    }

    public record Page(List<Map<String, Object>> items, long total, int page, int size,
                       int totalPages) {
    }

    public record Error(String code, String message) {
    }
}
