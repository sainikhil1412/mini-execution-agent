package com.agent.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AgentException.class)
    public ResponseEntity<Map<String, Object>> handleAgentException(AgentException ex) {
        log.warn("Handling AgentException: {} - {}", ex.getErrorCode(), ex.getMessage());
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("timestamp",  LocalDateTime.now().toString());
        error.put("status",     "ERROR");
        error.put("error_code", ex.getErrorCode());
        error.put("message",    ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Handling unexpected exception: {}", ex.getMessage(), ex);
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("timestamp",  LocalDateTime.now().toString());
        error.put("status",     "ERROR");
        error.put("error_code", "INTERNAL_ERROR");
        error.put("message",    ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
