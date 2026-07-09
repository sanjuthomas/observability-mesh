package com.observabilitymesh.sequence.service;

import com.observabilitymesh.sequence.model.NextSecurityEventSequenceRequest;
import com.observabilitymesh.sequence.model.NextSequenceRequest;
import com.observabilitymesh.sequence.repo.SequenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SequenceServiceTest {

    @Mock
    SequenceRepository repository;

    @InjectMocks
    SequenceService sequenceService;

    @Test
    void nextReturnsFormattedSequenceId() {
        when(repository.allocateNext("20260707-FICC-I")).thenReturn(7L);
        var response = sequenceService.next(new NextSequenceRequest("20260707", "FICC", "I"));
        assertThat(response.sequenceId()).isEqualTo("20260707-FICC-I-7");
        assertThat(response.sequenceNumber()).isEqualTo(7L);
    }

    @Test
    void nextSecurityEventReturnsFormattedSequenceId() {
        when(repository.allocateNext("inst-1-SE")).thenReturn(4L);
        var response = sequenceService.nextSecurityEvent(new NextSecurityEventSequenceRequest("inst-1"));
        assertThat(response.sequenceId()).isEqualTo("inst-1-SE-4");
        assertThat(response.resourceId()).isEqualTo("inst-1");
    }
}
