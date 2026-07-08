package com.observabilitymesh.sloauthor.exception;

import com.observabilitymesh.sloauthor.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OpenSloValidationException.class)
    public ResponseEntity<ApiError> handleValidation(OpenSloValidationException ex) {
        return ResponseEntity.badRequest().body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateOpenSloException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateOpenSloException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(OpenSloNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(OpenSloNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiError(ex.getMessage()));
    }
}
