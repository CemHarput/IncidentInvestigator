package com.CemHarput.IncidentInvestigator.common.exception;

import com.CemHarput.IncidentInvestigator.incident.exception.IncidentNotFoundException;
import com.CemHarput.IncidentInvestigator.incident.exception.InvalidIncidentStateException;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IncidentNotFoundException.class)
    public ResponseEntity<ApiError> handleIncidentNotFound(IncidentNotFoundException ex) {
        ApiError error = new ApiError(
                "INCIDENT_NOT_FOUND",
                ex.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(InvalidIncidentStateException.class)
    public ResponseEntity<ApiError> handleInvalidIncidentState(InvalidIncidentStateException ex) {
        ApiError error = new ApiError(
                "INVALID_INCIDENT_STATE",
                ex.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed");

        ApiError error = new ApiError(
                "VALIDATION_ERROR",
                message,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
