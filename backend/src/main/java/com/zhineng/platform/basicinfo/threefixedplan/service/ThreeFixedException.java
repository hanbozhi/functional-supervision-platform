package com.zhineng.platform.basicinfo.threefixedplan.service;

import org.springframework.http.HttpStatus;

public class ThreeFixedException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ThreeFixedException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
