package com.zhineng.platform.basicinfo.indicator.service;

import org.springframework.http.HttpStatus;

public class IndicatorException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public IndicatorException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
}
