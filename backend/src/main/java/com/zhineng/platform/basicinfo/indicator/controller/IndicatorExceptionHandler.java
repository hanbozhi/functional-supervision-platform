package com.zhineng.platform.basicinfo.indicator.controller;

import com.zhineng.platform.basicinfo.indicator.dto.IndicatorDtos;
import com.zhineng.platform.basicinfo.indicator.service.IndicatorException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = IndicatorController.class)
public class IndicatorExceptionHandler {
    @ExceptionHandler(IndicatorException.class)
    public ResponseEntity<IndicatorDtos.Error> handle(IndicatorException exception) {
        return ResponseEntity.status(exception.status())
                .body(new IndicatorDtos.Error(exception.code(), exception.getMessage()));
    }
}
