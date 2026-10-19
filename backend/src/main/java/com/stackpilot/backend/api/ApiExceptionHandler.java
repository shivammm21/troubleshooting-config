package com.stackpilot.backend.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", safe(ex.getMessage()),
                "code", "BAD_REQUEST"
        ));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", safe(ex.getMessage()),
                "code", "NOT_FOUND"
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", safe(ex.getMessage()),
                "code", "CONFLICT"
        ));
    }

    private static String safe(String message) {
        return message == null || message.isBlank() ? "Unexpected error" : message;
    }
}
