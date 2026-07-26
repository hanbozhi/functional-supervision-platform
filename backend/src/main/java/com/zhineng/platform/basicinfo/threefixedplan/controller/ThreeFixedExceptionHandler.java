package com.zhineng.platform.basicinfo.threefixedplan.controller;

import com.zhineng.platform.basicinfo.threefixedplan.dto.ThreeFixedDtos;
import com.zhineng.platform.basicinfo.threefixedplan.service.ThreeFixedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(assignableTypes = ThreeFixedPlanController.class)
public class ThreeFixedExceptionHandler {
    @ExceptionHandler(ThreeFixedException.class)
    public ResponseEntity<ThreeFixedDtos.Error> handle(ThreeFixedException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ThreeFixedDtos.Error(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ThreeFixedDtos.Error> tooLarge() {
        return ResponseEntity.badRequest()
                .body(new ThreeFixedDtos.Error("UPLOAD_TOO_LARGE", "上传内容超过允许大小"));
    }
}
