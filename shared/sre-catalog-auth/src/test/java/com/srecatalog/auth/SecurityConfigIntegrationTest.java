package com.srecatalog.auth;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = SecurityIntegrationTestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigIntegrationTest {

    @LocalServerPort
    int port;

    @org.junit.jupiter.api.Test
    void protectedApiRequiresAuthentication() {
        RestClient client = RestClient.builder().build();
        assertThatThrownBy(() -> client.get()
                .uri("http://localhost:" + port + "/api/v1/instructions")
                .retrieve()
                .toEntity(String.class))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @org.junit.jupiter.api.Test
    void healthEndpointIsPublic() {
        RestClient client = RestClient.builder().build();
        ResponseEntity<String> response = client.get()
                .uri("http://localhost:" + port + "/health")
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
