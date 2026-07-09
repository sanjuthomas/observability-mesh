package com.observabilitymesh.auth;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = SecurityIntegrationTestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "observability-mesh.auth.enabled=false")
class SecurityConfigDisabledIntegrationTest {

    @LocalServerPort
    int port;

    @org.junit.jupiter.api.Test
    void protectedApiIsOpenWhenAuthDisabled() {
        RestClient client = RestClient.builder().build();
        ResponseEntity<String> response = client.get()
                .uri("http://localhost:" + port + "/api/v1/instructions")
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
