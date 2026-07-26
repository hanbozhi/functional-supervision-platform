package com.zhineng.platform.basicinfo.evaluationarchive.service;

import org.springframework.http.HttpStatus;

public class EvaluationArchiveException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public EvaluationArchiveException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
