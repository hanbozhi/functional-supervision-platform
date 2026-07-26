package com.zhineng.platform.evaluation.selfevaluation.dto;
import java.util.List;
public final class SelfEvaluationDtos {
 private SelfEvaluationDtos(){}
 public record TaskRequest(String taskCode,String taskName,Integer evaluationYear,String taskType,String startDate,String endDate,String description,Long indicatorVersionId,List<Long> orgUnitIds){}
 public record EntryRequest(Double selfScore,String performanceDescription,String completionStatus,Integer rowVersion){}
 public record ReviewRequest(String action,String opinion){}
 public record RequirementRequest(Integer requiredMaterialCount,String allowedExtensions,String namingKeywords,String dueDate){}
 public record MaterialRequest(String materialName,String category,String description,String versionGroup,Boolean confirmClassification,String documentDate){}
 public record WarningProcessRequest(String status,String opinion){}
}
