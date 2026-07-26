package com.zhineng.platform.basicinfo.orgunit.controller;

import com.zhineng.platform.basicinfo.orgunit.dto.OrgUnitDtos;
import com.zhineng.platform.basicinfo.orgunit.service.OrgUnitBusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OrgUnitController.class)
public class OrgUnitExceptionHandler {
    @ExceptionHandler(OrgUnitBusinessException.class)
    public ResponseEntity<OrgUnitDtos.Error> handle(OrgUnitBusinessException exception) {
        return ResponseEntity.status(exception.status())
                .body(new OrgUnitDtos.Error(exception.code(), exception.getMessage()));
    }
}
