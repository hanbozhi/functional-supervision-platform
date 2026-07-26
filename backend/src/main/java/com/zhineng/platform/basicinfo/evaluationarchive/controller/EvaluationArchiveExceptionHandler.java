package com.zhineng.platform.basicinfo.evaluationarchive.controller;

import com.zhineng.platform.basicinfo.evaluationarchive.dto.EvaluationArchiveDtos;
import com.zhineng.platform.basicinfo.evaluationarchive.service.EvaluationArchiveException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = EvaluationArchiveController.class)
public class EvaluationArchiveExceptionHandler {
    @ExceptionHandler(EvaluationArchiveException.class)
    public ResponseEntity<EvaluationArchiveDtos.Error> handle(
            EvaluationArchiveException exception
    ) {
        return ResponseEntity.status(exception.status())
                .body(new EvaluationArchiveDtos.Error(exception.code(), exception.getMessage()));
    }
}
