package com.zhineng.platform.evaluation.performance.controller;
import com.zhineng.platform.evaluation.performance.dto.PerformanceDtos;
import com.zhineng.platform.evaluation.performance.service.PerformanceException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
@RestControllerAdvice(assignableTypes=PerformanceController.class)
public class PerformanceExceptionHandler{
 @ExceptionHandler(PerformanceException.class) public ResponseEntity<PerformanceDtos.Error> handle(PerformanceException e){return ResponseEntity.status(e.status()).body(new PerformanceDtos.Error(e.code(),e.getMessage()));}
}
