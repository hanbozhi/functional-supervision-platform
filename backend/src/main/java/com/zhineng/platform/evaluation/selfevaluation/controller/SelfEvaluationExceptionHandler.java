package com.zhineng.platform.evaluation.selfevaluation.controller;
import com.zhineng.platform.evaluation.selfevaluation.service.SelfEvaluationException;import java.util.Map;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=SelfEvaluationController.class)public class SelfEvaluationExceptionHandler{@ExceptionHandler(SelfEvaluationException.class)ResponseEntity<Map<String,Object>>handle(SelfEvaluationException e){return ResponseEntity.status(e.status()).body(Map.of("code",e.code(),"message",e.getMessage()));}}
