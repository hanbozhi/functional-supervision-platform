package com.zhineng.platform.evaluation.performance.dto;
public final class PerformanceDtos {
    private PerformanceDtos(){}
    public record MappingRequest(String sourceField,String targetField,Boolean required,Integer sortOrder,Integer rowVersion){}
    public record StatusRequest(String status,Integer rowVersion){}
    public record CorrectionRequest(Long rawRecordId,String correctionScope,String correctedGrade,Double correctedKeyWorkScore,String correctedLeadershipRating,String correctionReason,Integer rowVersion){}
    public record ReviewRequest(String action,String opinion,Integer rowVersion){}
    public record Error(String code,String message){}
}
