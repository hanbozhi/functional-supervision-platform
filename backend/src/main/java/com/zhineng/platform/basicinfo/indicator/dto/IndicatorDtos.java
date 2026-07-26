package com.zhineng.platform.basicinfo.indicator.dto;

import java.util.Map;

public final class IndicatorDtos {
    private IndicatorDtos() {
    }

    public record SystemRequest(
            String systemCode, String systemName, Integer evaluationYear,
            String applicableOrgType, String description
    ) {
    }

    public record ItemRequest(
            Long versionId, Long parentId, Integer indicatorLevel, String indicatorCode,
            String indicatorName, Double standardScore, Double weight,
            String indicatorType, String evaluationMethod, Integer sortOrder,
            Integer rowVersion
    ) {
    }

    public record RuleRequest(
            Long indicatorId, String ruleType, String ruleName,
            Map<String, Object> config, String description, Integer sortOrder,
            Integer rowVersion
    ) {
    }

    public record StatusRequest(String status, Integer rowVersion) {
    }

    public record CopyVersionRequest(Integer targetYear, String versionName) {
    }

    public record TemplateRequest(
            Long sourceVersionId, String templateCode, String templateName,
            String applicableOrgType, String description
    ) {
    }

    public record TemplateCopyRequest(String templateCode, String templateName) {
    }

    public record TemplateInitializeRequest(
            String systemCode, String systemName, Integer evaluationYear,
            String applicableOrgType, String description
    ) {
    }

    public record Error(String code, String message) {
    }
}
