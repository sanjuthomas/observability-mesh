package com.srecatalog.instruction.web;

import com.srecatalog.instruction.repo.ConcurrentModificationException;
import com.srecatalog.instruction.repo.InstructionNotFoundException;
import com.srecatalog.instruction.service.InvalidStateTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructionExceptionHandlerTest {

    @Mock HttpServletRequest request;

    private final InstructionExceptionHandler handler = new InstructionExceptionHandler();

    @Test
    void mapsNotFoundTo404() {
        when(request.getRequestURI()).thenReturn("/api/v1/instructions/I-1");
        var response = handler.handleNotFound(new InstructionNotFoundException("I-1"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void mapsConflictTo409() {
        when(request.getRequestURI()).thenReturn("/api/v1/instructions/I-1/submit");
        var response = handler.handleConflict(new InvalidStateTransitionException("bad state"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        var concurrent = handler.handleConflict(new ConcurrentModificationException("retry"), request);
        assertThat(concurrent.getStatusCode().value()).isEqualTo(409);
    }
}
