package com.observabilitymesh.payment.web;

import com.observabilitymesh.payment.repo.ConcurrentModificationException;
import com.observabilitymesh.payment.repo.PaymentNotFoundException;
import com.observabilitymesh.common.model.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentExceptionHandlerTest {

    @Mock HttpServletRequest request;

    private final PaymentExceptionHandler handler = new PaymentExceptionHandler();

    @Test
    void handlesNotFound() {
        when(request.getRequestURI()).thenReturn("/api/v1/payments/P-1");
        ResponseEntity<ApiError> response = handler.handleNotFound(new PaymentNotFoundException("P-1"), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
    }

    @Test
    void handlesConcurrentModification() {
        when(request.getRequestURI()).thenReturn("/api/v1/payments/P-1");
        ResponseEntity<ApiError> response = handler.handleConcurrent(
                new ConcurrentModificationException("conflict"), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
