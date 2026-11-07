package com.agrodistribuye.api;

import java.util.Map;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ValidationAdvice {
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String,String>> invalid(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause instanceof IllegalArgumentException ? cause.getMessage()
                : "Formato de datos inválido. Revisa campos, números, unidades y dimensiones de las tablas.";
        return ResponseEntity.badRequest().body(Map.of("status","INVALID_INPUT","message",message == null ? "Datos inválidos." : message));
    }
}
