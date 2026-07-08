package com.srecatalog.sequenceclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SequenceClient {

    private final RestClient restClient;

    public SequenceClient(RestClient.Builder builder, @Value("${sre-catalog.sequence.url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl.replaceAll("/$", "")).build();
    }

    public String nextInstructionId(String businessDate, String owningLob) {
        return next(businessDate, owningLob, "I").sequenceId();
    }

    public String nextPaymentId(String businessDate, String owningLob) {
        return next(businessDate, owningLob, "P").sequenceId();
    }

    public String nextSecurityEventId(String resourceId) {
        NextSecurityEventResponse response = restClient.post()
                .uri("/api/v1/sequences/security-events/next")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new NextSecurityEventRequest(resourceId))
                .retrieve()
                .body(NextSecurityEventResponse.class);
        if (response == null) {
            throw new SequenceClientException("Empty response from sequence-service");
        }
        return response.sequenceId();
    }

    private NextSequenceResponse next(String businessDate, String owningLob, String entityType) {
        NextSequenceResponse response = restClient.post()
                .uri("/api/v1/sequences/next")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new NextSequenceRequest(businessDate, owningLob, entityType))
                .retrieve()
                .body(NextSequenceResponse.class);
        if (response == null) {
            throw new SequenceClientException("Empty response from sequence-service");
        }
        return response;
    }

    private record NextSequenceRequest(String businessDate, String owningLob, String entityType) {}
    private record NextSequenceResponse(String sequenceId) {}
    private record NextSecurityEventRequest(String resourceId) {}
    private record NextSecurityEventResponse(String sequenceId) {}
}
