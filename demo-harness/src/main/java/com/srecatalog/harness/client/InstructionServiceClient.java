package com.srecatalog.harness.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srecatalog.harness.config.HarnessProperties;
import com.srecatalog.harness.model.SessionCredentials;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class InstructionServiceClient {

    private final RestClient restClient;
    private final HarnessProperties properties;
    private final ObjectMapper objectMapper;

    public InstructionServiceClient(
            RestClient.Builder builder,
            HarnessProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ServiceResponse createInstruction(SessionCredentials session, Map<String, Object> payload) {
        return request(HttpMethod.POST, "/instructions", session, payload);
    }

    public ServiceResponse getInstruction(SessionCredentials session, String instructionId) {
        return request(HttpMethod.GET, "/instructions/" + instructionId, session, null);
    }

    public ServiceResponse listInstructions(SessionCredentials session, String status, int limit) {
        String path = "/instructions?limit=" + limit;
        if (status != null && !status.isBlank()) {
            path += "&status=" + status;
        }
        return request(HttpMethod.GET, path, session, null);
    }

    public ServiceResponse submitInstruction(SessionCredentials session, String instructionId) {
        return request(HttpMethod.POST, "/instructions/" + instructionId + "/submit", session, null);
    }

    public ServiceResponse approveInstruction(SessionCredentials session, String instructionId) {
        return request(HttpMethod.POST, "/instructions/" + instructionId + "/approve", session, null);
    }

    public ServiceResponse rejectInstruction(SessionCredentials session, String instructionId, String reason) {
        return request(HttpMethod.POST, "/instructions/" + instructionId + "/reject", session, Map.of("reason", reason));
    }

    public ServiceResponse suspendInstruction(SessionCredentials session, String instructionId) {
        return request(HttpMethod.POST, "/instructions/" + instructionId + "/suspend", session, null);
    }

    public ServiceResponse reactivateInstruction(SessionCredentials session, String instructionId) {
        return request(HttpMethod.POST, "/instructions/" + instructionId + "/reactivate", session, null);
    }

    public ServiceResponse listVersions(SessionCredentials session, String instructionId) {
        return request(HttpMethod.GET, "/instructions/" + instructionId + "/versions", session, null);
    }

    private ServiceResponse request(
            HttpMethod method,
            String path,
            SessionCredentials session,
            Object body) {
        String url = properties.instructionServiceUrl().replaceAll("/$", "")
                + properties.instructionServiceApiPrefix()
                + path;
        RestClient.RequestBodySpec spec = restClient.method(method)
                .uri(url)
                .headers(headers -> applySession(headers, session));
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

    static void applySession(HttpHeaders headers, SessionCredentials session) {
        headers.setBearerAuth(session.sessionToken());
        headers.set("X-Session-Id", session.sessionId());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (headers.getContentType() == null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
    }
}
