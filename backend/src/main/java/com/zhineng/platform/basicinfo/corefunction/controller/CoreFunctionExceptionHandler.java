package com.zhineng.platform.basicinfo.corefunction.controller;

import com.zhineng.platform.basicinfo.corefunction.dto.CoreFunctionDtos;
import com.zhineng.platform.basicinfo.corefunction.service.CoreFunctionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CoreFunctionController.class)
public class CoreFunctionExceptionHandler {
    @ExceptionHandler(CoreFunctionException.class)
    public ResponseEntity<CoreFunctionDtos.Error> handle(CoreFunctionException exception) {
        return ResponseEntity.status(exception.status())
                .body(new CoreFunctionDtos.Error(exception.code(), exception.getMessage()));
    }
}
