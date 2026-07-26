package com.zhineng.platform.common.user.dto;

import java.util.List;

public record CurrentUserResponse(
        Long id,
        String username,
        String displayName,
        Long orgUnitId,
        String orgUnitName,
        List<String> roleCodes
) {
}
