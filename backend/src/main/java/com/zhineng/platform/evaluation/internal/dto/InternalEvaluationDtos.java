package com.zhineng.platform.evaluation.internal.dto;

import java.util.List;

public final class InternalEvaluationDtos {
    private InternalEvaluationDtos() {
    }

    public record TaskRequest(
            String taskCode, String taskName, Integer evaluationYear, String taskType,
            String startDate, String endDate, String description, Long indicatorVersionId,
            List<Long> orgUnitIds, Long evaluatorId, Long reviewerId
    ) {
    }

    public record CopyRequest(String taskCode, String taskName, Integer evaluationYear) {
    }

    public record StatusRequest(String status, String reason, Integer rowVersion) {
    }

    public record ScoreInput(
            Long entryId, Double score, String basisType, String scoreBasis,
            String remarks, Boolean vetoTriggered, Integer rowVersion
    ) {
    }

    public record SaveScoresRequest(List<ScoreInput> entries, Integer sheetRowVersion) {
    }

    public record ReviewRequest(String action, String opinion, Integer rowVersion) {
    }

    public record Error(String code, String message) {
    }
}
