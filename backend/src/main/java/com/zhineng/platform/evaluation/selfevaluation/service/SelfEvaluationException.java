package com.zhineng.platform.evaluation.selfevaluation.service;
import org.springframework.http.HttpStatus;
public class SelfEvaluationException extends RuntimeException{private final HttpStatus status;private final String code;public SelfEvaluationException(HttpStatus s,String c,String m){super(m);status=s;code=c;}public HttpStatus status(){return status;}public String code(){return code;}}
