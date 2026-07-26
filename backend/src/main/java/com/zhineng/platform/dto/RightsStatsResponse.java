package com.zhineng.platform.dto;

import java.util.List;

public record RightsStatsResponse(
        long totalItems,
        long totalSourceFiles,
        long totalDepartments,
        long totalPowerTypes,
        List<CountItem> powerTypeDistribution,
        List<CountItem> departmentTop
) {
}
