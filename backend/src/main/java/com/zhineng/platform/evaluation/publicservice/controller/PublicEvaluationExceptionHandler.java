package com.zhineng.platform.evaluation.publicservice.controller;
import com.zhineng.platform.evaluation.publicservice.service.PublicEvaluationException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=PublicEvaluationController.class)
public class PublicEvaluationExceptionHandler {
 @ExceptionHandler(PublicEvaluationException.class) ResponseEntity<Map<String,Object>> handle(PublicEvaluationException e){
  return ResponseEntity.status(e.status()).body(Map.of("code",e.code(),"message",e.getMessage()));
 }
}
