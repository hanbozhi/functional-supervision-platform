package com.zhineng.platform.evaluation.publicservice.dto;

public final class PublicEvaluationDtos {
    private PublicEvaluationDtos() {}
    public record ServiceItemRequest(String itemCode,String itemName,Long orgUnitId,String description,String status) {}
    public record EvaluationRequest(Long orgUnitId,Long serviceItemId,Integer convenienceScore,Integer attitudeScore,
                                    Integer timelinessScore,Integer clarityScore,String commentText,Boolean anonymous,
                                    String evaluatorName,String evaluatorPhone,String evaluatorIdNo) {}
    public record ProcessRequest(String status,String opinion) {}
    public record AccessRequest(String reason,String requestedFields) {}
    public record ReviewRequest(String action,String opinion) {}
    public record SentimentRequest(String sentiment) {}
}
