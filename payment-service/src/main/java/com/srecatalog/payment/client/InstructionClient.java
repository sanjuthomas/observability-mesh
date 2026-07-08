package com.srecatalog.payment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srecatalog.auth.RequestSubjectResolver;
import com.srecatalog.payment.config.ServiceIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class InstructionClient {

    private final RestClient restClient;
    private final ServiceIdentity serviceIdentity;
    private final ObjectMapper objectMapper;

    public InstructionClient(
            RestClient.Builder builder,
            ServiceIdentity serviceIdentity,
            ObjectMapper objectMapper,
            @Value("${sre-catalog.instruction.url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl.replaceAll("/$", "")).build();
        this.serviceIdentity = serviceIdentity;
        this.objectMapper = objectMapper;
    }

    public JsonNode getInstruction(String instructionId, String bearerToken, String sessionId) {
        return get("/api/v1/instructions/" + instructionId, oboHeaders(bearerToken, sessionId));
    }

    public JsonNode getInstructionAsService(String instructionId) {
        serviceIdentity.ensureLoggedIn();
        HttpHeaders headers = serviceHeaders();
        return get("/api/v1/instructions/" + instructionId, headers);
    }

    public JsonNode markUsed(String instructionId, String paymentId, String bearerToken, String sessionId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payment_reference", paymentId);
        body.put("end_to_end_identification", paymentId.length() > 35 ? paymentId.substring(0, 35) : paymentId);
        return post("/api/v1/instructions/" + instructionId + "/use", body, oboHeaders(bearerToken, sessionId));
    }

    public JsonNode releaseUse(String instructionId, String paymentId, String bearerToken, String sessionId) {
        Map<String, Object> body = Map.of("payment_reference", paymentId);
        return post("/api/v1/instructions/" + instructionId + "/release-use", body, oboHeaders(bearerToken, sessionId));
    }

    private JsonNode get(String path, HttpHeaders headers) {
        try {
            String body = restClient.get()
                    .uri(path)
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new InstructionNotFoundException(extractId(path));
            }
            throw new InstructionClientException("instruction-service error: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            throw new InstructionClientException("instruction-service error: " + ex.getMessage(), ex);
        }
    }

    private JsonNode post(String path, Map<String, Object> body, HttpHeaders headers) {
        try {
            String response = restClient.post()
                    .uri(path)
                    .headers(h -> h.addAll(headers))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(response);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new InstructionNotFoundException(extractId(path));
            }
            if (ex.getStatusCode().value() == 409) {
                throw new InstructionStateException(ex.getResponseBodyAsString());
            }
            throw new InstructionClientException("instruction-service error: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            throw new InstructionClientException("instruction-service error: " + ex.getMessage(), ex);
        }
    }

    private HttpHeaders oboHeaders(String bearerToken, String sessionId) {
        serviceIdentity.ensureLoggedIn();
        String svcToken = serviceIdentity.token();
        HttpHeaders headers = new HttpHeaders();
        if (svcToken != null && bearerToken != null) {
            headers.setBearerAuth(stripBearer(svcToken));
            if (serviceIdentity.sessionId() != null) {
                headers.set(RequestSubjectResolver.SESSION_HEADER, serviceIdentity.sessionId());
            }
            headers.set(RequestSubjectResolver.OBO_HEADER, bearerToken);
            if (sessionId != null && !sessionId.isBlank()) {
                headers.set(RequestSubjectResolver.OBO_SESSION_HEADER, sessionId);
            }
            return headers;
        }
        if (bearerToken != null) {
            headers.setBearerAuth(stripBearer(bearerToken));
        }
        if (sessionId != null && !sessionId.isBlank()) {
            headers.set(RequestSubjectResolver.SESSION_HEADER, sessionId);
        }
        return headers;
    }

    private HttpHeaders serviceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (serviceIdentity.token() != null) {
            headers.setBearerAuth(stripBearer(serviceIdentity.token()));
        }
        if (serviceIdentity.sessionId() != null) {
            headers.set(RequestSubjectResolver.SESSION_HEADER, serviceIdentity.sessionId());
        }
        return headers;
    }

    private static String stripBearer(String token) {
        return token.startsWith("Bearer ") ? token.substring(7) : token;
    }

    private static String extractId(String path) {
        String[] parts = path.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isBlank() && !parts[i].equals("use") && !parts[i].equals("release-use")) {
                return parts[i];
            }
        }
        return "unknown";
    }
}
