package com.zhineng.platform.evaluation.internal.controller;

import com.zhineng.platform.evaluation.internal.dto.InternalEvaluationDtos;
import com.zhineng.platform.evaluation.internal.service.InternalEvaluationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = InternalEvaluationController.class)
public class InternalEvaluationExceptionHandler {
    @ExceptionHandler(InternalEvaluationException.class)
    public ResponseEntity<InternalEvaluationDtos.Error> handle(
            InternalEvaluationException exception
    ) {
        return ResponseEntity.status(exception.status())
                .body(new InternalEvaluationDtos.Error(exception.code(), exception.getMessage()));
    }
}
