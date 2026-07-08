package com.observabilitymesh.harness.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.harness.config.HarnessProperties;
import com.observabilitymesh.harness.model.SessionCredentials;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class PaymentServiceClient {

    private final RestClient restClient;
    private final HarnessProperties properties;
    private final ObjectMapper objectMapper;

    public PaymentServiceClient(
            RestClient.Builder builder,
            HarnessProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ServiceResponse createPayment(
            SessionCredentials session,
            String instructionId,
            double amount,
            String valueDate) {
        return request(HttpMethod.POST, "/payments", session, Map.of(
                "instruction_id", instructionId,
                "amount", amount,
                "value_date", valueDate));
    }

    public ServiceResponse submitPayment(SessionCredentials session, String paymentId) {
        return request(HttpMethod.POST, "/payments/" + paymentId + "/submit", session, null);
    }

    public ServiceResponse approvePayment(SessionCredentials session, String paymentId) {
        return request(HttpMethod.POST, "/payments/" + paymentId + "/approve", session, null);
    }

    public ServiceResponse rejectPayment(SessionCredentials session, String paymentId, String reason) {
        return request(HttpMethod.POST, "/payments/" + paymentId + "/reject", session, Map.of("reason", reason));
    }

    public ServiceResponse updatePayment(
            SessionCredentials session,
            String paymentId,
            String instructionId,
            double amount,
            String valueDate) {
        return request(HttpMethod.PUT, "/payments/" + paymentId, session, Map.of(
                "instruction_id", instructionId,
                "amount", amount,
                "value_date", valueDate));
    }

    public ServiceResponse listPayments(SessionCredentials session, String status, int limit) {
        String path = "/payments?limit=" + limit;
        if (status != null && !status.isBlank()) {
            path += "&status=" + status;
        }
        return request(HttpMethod.GET, path, session, null);
    }

    private ServiceResponse request(
            HttpMethod method,
            String path,
            SessionCredentials session,
            Object body) {
        String url = properties.paymentServiceUrl().replaceAll("/$", "")
                + properties.paymentServiceApiPrefix()
                + path;
        RestClient.RequestBodySpec spec = restClient.method(method)
                .uri(url)
                .headers(headers -> InstructionServiceClient.applySession(headers, session));
        if (body != null) {
            spec = spec.body(body);
        }
        return spec.exchange((request, response) -> {
                    byte[] bytes = response.getBody().readAllBytes();
                    String text = new String(bytes);
                    JsonNode json = parseJson(text);
                    return new ServiceResponse(response.getStatusCode().value(), text, json);
                });
    }

    private JsonNode parseJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception ex) {
            return null;
        }
    }
}
