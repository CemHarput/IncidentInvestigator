package com.CemHarput.IncidentInvestigator.common.exception;

import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisAlreadyRunningException;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisNotAllowedException;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisMessagingException;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalyzerDownstreamException;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalyzerUnavailableException;
import com.CemHarput.IncidentInvestigator.analysis.exception.InvalidAnalyzerRequestException;
import com.CemHarput.IncidentInvestigator.analysis.exception.InvalidAnalyzerResponseException;
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

    @ExceptionHandler(AnalysisNotAllowedException.class)
    public ResponseEntity<ApiError> handleAnalysisNotAllowed(AnalysisNotAllowedException ex) {
        ApiError error = new ApiError(
                "ANALYSIS_NOT_ALLOWED",
                ex.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(AnalysisAlreadyRunningException.class)
    public ResponseEntity<ApiError> handleAnalysisAlreadyRunning(AnalysisAlreadyRunningException ex) {
        ApiError error = new ApiError(
                "ANALYSIS_ALREADY_RUNNING",
                ex.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(AnalyzerUnavailableException.class)
    public ResponseEntity<ApiError> handleAnalyzerUnavailable(AnalyzerUnavailableException ex) {
        ApiError error = new ApiError(
                "ANALYZER_UNAVAILABLE",
                ex.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(AnalysisMessagingException.class)
    public ResponseEntity<ApiError> handleAnalysisMessaging(AnalysisMessagingException ex) {
        ApiError error = new ApiError(
                "ANALYSIS_MESSAGING_UNAVAILABLE",
                ex.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler({AnalyzerDownstreamException.class, InvalidAnalyzerRequestException.class})
    public ResponseEntity<ApiError> handleAnalyzerDownstream(RuntimeException ex) {
        ApiError error = new ApiError(
                "ANALYZER_DOWNSTREAM_ERROR",
                ex.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    @ExceptionHandler(InvalidAnalyzerResponseException.class)
    public ResponseEntity<ApiError> handleInvalidAnalyzerResponse(InvalidAnalyzerResponseException ex) {
        ApiError error = new ApiError(
                "INVALID_ANALYZER_RESPONSE",
                ex.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
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
