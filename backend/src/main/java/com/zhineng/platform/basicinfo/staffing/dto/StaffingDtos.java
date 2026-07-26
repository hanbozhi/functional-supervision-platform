package com.zhineng.platform.basicinfo.staffing.dto;

import java.util.List;

public final class StaffingDtos {
    private StaffingDtos() {
    }

    public record ListItem(
            Long id, long orgUnitId, String unitCode, String unitName, String unitType,
            String unitStatus, Integer approvedStaffing, Integer actualStaffing,
            Integer leadershipPositionsApproved, Integer leadershipPositionsOccupied,
            Integer externalStaff, String dataDate, String remarks,
            String lastChangeSummary, Integer versionNo, String updatedByName,
            String updatedAt, boolean maintained, boolean overstaffed,
            boolean leadershipOverOccupied, Double utilizationRate
    ) {
    }

    public record Page(List<ListItem> items, long total, int page, int size, int totalPages) {
    }

    public record Stats(
            long approvedStaffing, long actualStaffing, long externalStaff,
            double utilizationRate, long maintainedUnits, long totalUnits,
            long overstaffedUnits, long leadershipOverOccupiedUnits
    ) {
    }

    public record SaveRequest(
            Long orgUnitId, Integer approvedStaffing, Integer actualStaffing,
            Integer leadershipPositionsApproved, Integer leadershipPositionsOccupied,
            Integer externalStaff, String dataDate, String changeReason,
            String remarks, Integer versionNo
    ) {
    }

    public record BatchItem(
            Long id, Integer approvedStaffing, Integer actualStaffing,
            Integer leadershipPositionsApproved, Integer leadershipPositionsOccupied,
            Integer externalStaff, Integer versionNo, String remarks
    ) {
    }

    public record BatchRequest(List<BatchItem> items, String dataDate, String changeReason) {
    }

    public record ChangeLog(
            long id, String changeGroupNo, String changeSource,
            Integer approvedStaffingBefore, Integer approvedStaffingAfter,
            Integer actualStaffingBefore, Integer actualStaffingAfter,
            Integer leadershipApprovedBefore, Integer leadershipApprovedAfter,
            Integer leadershipOccupiedBefore, Integer leadershipOccupiedAfter,
            Integer externalStaffBefore, Integer externalStaffAfter,
            String changedFields, String dataDate, String changeReason,
            String operatorName, String operatedAt
    ) {
    }

    public record ChangePage(
            List<ChangeLog> items, long total, int page, int size, int totalPages
    ) {
    }

    public record ImportError(
            int rowNumber, String orgUnitCode, String orgUnitName, String errorMessage
    ) {
    }

    public record ImportResult(
            long batchId, String batchNo, String fileName, int totalRows,
            int successRows, int failedRows, int warningRows, String status,
            List<String> warnings, List<ImportError> errors
    ) {
    }

    public record Error(String code, String message) {
    }
}
