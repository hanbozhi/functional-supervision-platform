package com.zhineng.platform.basicinfo.threefixedplan.dto;

import java.util.List;

public final class ThreeFixedDtos {
    private ThreeFixedDtos() {
    }

    public record Fields(
            String planName, String documentNo, String effectiveDate,
            String organizationName, String organizationNature, String staffingType,
            Integer approvedStaffing, String mainResponsibilities,
            String internalDepartments, String remarks
    ) {
    }

    public record ManualRequest(Long orgUnitId, Fields fields) {
    }

    public record UpdateRequest(Fields fields, Integer rowVersion) {
    }

    public record ReviewRequest(String result, String opinion, Integer rowVersion) {
    }

    public record SubmitRequest(Integer rowVersion) {
    }

    public record MappingRequest(
            String fileType, String sourceLabel, String targetField,
            Integer sortOrder, Integer rowVersion
    ) {
    }

    public record MappingStatusRequest(String status) {
    }

    public record BatchItem(Long orgUnitId, String planName) {
    }

    public record BatchResult(int index, String fileName, boolean success, Long versionId, String message) {
    }

    public record Error(String code, String message) {
    }

    public record Page(List<?> items, long total, int page, int size, int totalPages) {
    }
}
