package com.srecatalog.instruction.web;

import com.srecatalog.common.model.ApiError;
import com.srecatalog.instruction.repo.ConcurrentModificationException;
import com.srecatalog.instruction.repo.InstructionNotFoundException;
import com.srecatalog.instruction.service.InvalidStateTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InstructionExceptionHandler {

    @ExceptionHandler(InstructionNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(InstructionNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiError.of(404, "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler({ConcurrentModificationException.class, InvalidStateTransitionException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiError.of(409, "Conflict", ex.getMessage(), request.getRequestURI()));
    }
}
