package com.zhineng.platform.evaluation.counterpart.controller;

import com.zhineng.platform.evaluation.counterpart.dto.CounterpartDtos;
import com.zhineng.platform.evaluation.counterpart.service.CounterpartException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CounterpartController.class)
public class CounterpartExceptionHandler {
    @ExceptionHandler(CounterpartException.class)
    public ResponseEntity<CounterpartDtos.Error> handle(CounterpartException exception) {
        return ResponseEntity.status(exception.status())
                .body(new CounterpartDtos.Error(exception.code(), exception.getMessage()));
    }
}
