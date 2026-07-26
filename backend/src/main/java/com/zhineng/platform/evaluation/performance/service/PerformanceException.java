package com.zhineng.platform.evaluation.performance.service;
import org.springframework.http.HttpStatus;
public class PerformanceException extends RuntimeException{
    private final HttpStatus status;private final String code;
    public PerformanceException(HttpStatus status,String code,String message){super(message);this.status=status;this.code=code;}
    public HttpStatus status(){return status;}public String code(){return code;}
}
