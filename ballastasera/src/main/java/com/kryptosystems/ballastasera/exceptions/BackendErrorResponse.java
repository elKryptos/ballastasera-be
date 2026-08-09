package com.kryptosystems.ballastasera.exceptions;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class BackendErrorResponse {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorDetails> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getRequestURI(),
                "Resource not found",
                HttpStatus.NOT_FOUND
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorDetails);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDetails> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Validation failed",
                request.getRequestURI(),
                "One or more fields are invalid",
                HttpStatus.BAD_REQUEST,
                errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDetails);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDetails> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getRequestURI(),
                "Access denied",
                HttpStatus.FORBIDDEN
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorDetails);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDetails> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "An unexpected error occurred",
                request.getRequestURI(),
                ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDetails);
    }
}