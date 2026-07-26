package com.zhineng.platform.evaluation.counterpart.dto;

import java.util.List;

public final class CounterpartDtos {
    private CounterpartDtos() {
    }

    public record RelationRequest(
            Long subjectOrgId, Long counterpartOrgId, String collaborationItem,
            String source, Double confidence, Integer rowVersion
    ) {
    }

    public record VerifyRequest(String result, String opinion, Integer rowVersion) {
    }

    public record StatusRequest(String status, Integer rowVersion) {
    }

    public record DimensionInput(String code, String name, Integer sortOrder) {
    }

    public record QuestionInput(
            Long dimensionId, String dimensionCode, String code, String text,
            String type, Boolean required, Long indicatorItemId, Integer sortOrder
    ) {
    }

    public record QuestionnaireRequest(
            String batchCode, String title, Integer evaluationYear, String deadlineAt,
            String description, Long indicatorVersionId,
            List<DimensionInput> dimensions, List<QuestionInput> questions
    ) {
    }

    public record CopyQuestionnaireRequest(String batchCode, String title, Integer evaluationYear) {
    }

    public record RecipientRequest(List<Long> relationIds) {
    }

    public record AnswerInput(Long questionId, Integer scoreValue, String textValue) {
    }

    public record SubmitRequest(List<AnswerInput> answers, Integer elapsedSeconds) {
    }

    public record AssignRequest(Long userId, String opinion, Integer rowVersion) {
    }

    public record ReviewRequest(String action, String opinion, Integer rowVersion) {
    }

    public record Error(String code, String message) {
    }
}
