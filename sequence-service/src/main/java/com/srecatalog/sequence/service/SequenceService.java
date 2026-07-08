package com.srecatalog.sequence.service;

import com.srecatalog.sequence.format.SequenceFormatting;
import com.srecatalog.sequence.model.NextSecurityEventSequenceRequest;
import com.srecatalog.sequence.model.NextSecurityEventSequenceResponse;
import com.srecatalog.sequence.model.NextSequenceRequest;
import com.srecatalog.sequence.model.NextSequenceResponse;
import com.srecatalog.sequence.repo.SequenceRepository;
import org.springframework.stereotype.Service;

@Service
public class SequenceService {

    private final SequenceRepository repository;

    public SequenceService(SequenceRepository repository) {
        this.repository = repository;
    }

    public NextSequenceResponse next(NextSequenceRequest request) {
        String counterKey = SequenceFormatting.counterKey(
                request.businessDate(), request.owningLob(), request.entityType());
        long sequenceNumber = repository.allocateNext(counterKey);
        return new NextSequenceResponse(
                SequenceFormatting.sequenceId(counterKey, sequenceNumber),
                request.businessDate(),
                request.owningLob(),
                request.entityType(),
                sequenceNumber,
                counterKey);
    }

    public NextSecurityEventSequenceResponse nextSecurityEvent(NextSecurityEventSequenceRequest request) {
        String counterKey = SequenceFormatting.securityEventCounterKey(request.resourceId());
        long sequenceNumber = repository.allocateNext(counterKey);
        return new NextSecurityEventSequenceResponse(
                SequenceFormatting.securityEventSequenceId(request.resourceId(), sequenceNumber),
                request.resourceId(),
                sequenceNumber,
                counterKey);
    }
}
