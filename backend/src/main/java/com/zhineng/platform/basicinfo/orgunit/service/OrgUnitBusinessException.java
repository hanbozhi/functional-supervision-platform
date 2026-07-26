package com.zhineng.platform.basicinfo.orgunit.service;

import org.springframework.http.HttpStatus;

public class OrgUnitBusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public OrgUnitBusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
