package com.zhineng.platform.dto;

public record RightsItemResponse(
        Long id,
        String sourceFile,
        String sheetName,
        Integer sourceRowNumber,
        String department,
        String year,
        String sequenceNo,
        String itemName,
        String subitemName,
        String powerType,
        String basis,
        String exercisingBody,
        String undertakingOrg,
        String implementationLevelAuthority,
        String departmentDuty,
        String responsibilityContent,
        String responsibilityBasis,
        String accountabilityScope,
        String accountabilitySituation,
        String remark,
        String status,
        String rawJson
) {
}
