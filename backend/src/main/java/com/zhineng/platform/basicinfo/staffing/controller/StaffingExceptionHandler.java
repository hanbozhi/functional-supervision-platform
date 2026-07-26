package com.zhineng.platform.basicinfo.staffing.controller;

import com.zhineng.platform.basicinfo.staffing.dto.StaffingDtos;
import com.zhineng.platform.basicinfo.staffing.service.StaffingException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = StaffingController.class)
public class StaffingExceptionHandler {
    @ExceptionHandler(StaffingException.class)
    public ResponseEntity<StaffingDtos.Error> handle(StaffingException exception) {
        return ResponseEntity.status(exception.status())
                .body(new StaffingDtos.Error(exception.code(), exception.getMessage()));
    }
}
