package com.srecatalog.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srecatalog.payment.config.ServiceIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstructionClientTest {

    @Mock ServiceIdentity serviceIdentity;

    private MockRestServiceServer server;
    private InstructionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new InstructionClient(builder, serviceIdentity, new ObjectMapper(), "http://instruction.test");
        when(serviceIdentity.token()).thenReturn("svc-token");
        when(serviceIdentity.sessionId()).thenReturn("svc-session");
    }

    @Test
    void getInstructionUsesOboHeaders() {
        server.expect(requestTo("http://instruction.test/api/v1/instructions/I-1"))
                .andExpect(header("X-On-Behalf-Of", "user-token"))
                .andRespond(withSuccess("{\"instruction_id\":\"I-1\",\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));
        assertThat(client.getInstruction("I-1", "user-token", "user-session").get("status").asText())
                .isEqualTo("APPROVED");
        server.verify();
    }

    @Test
    void getInstructionWithoutServiceTokenForwardsUserBearer() {
        when(serviceIdentity.token()).thenReturn(null);
        server.expect(requestTo("http://instruction.test/api/v1/instructions/I-1"))
                .andExpect(header("Authorization", "Bearer user-token"))
                .andRespond(withSuccess("{\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));
        client.getInstruction("I-1", "user-token", "user-session");
        server.verify();
    }

    @Test
    void markUsedThrowsOnConflict() {
        server.expect(requestTo("http://instruction.test/api/v1/instructions/I-1/use"))
                .andRespond(withStatus(HttpStatus.CONFLICT).body("already used"));
        assertThatThrownBy(() -> client.markUsed("I-1", "P-1", "user-token", "user-session"))
                .isInstanceOf(InstructionStateException.class);
    }

    @Test
    void releaseUseThrowsNotFound() {
        server.expect(requestTo("http://instruction.test/api/v1/instructions/I-1/release-use"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.releaseUse("I-1", "P-1", "user-token", "user-session"))
                .isInstanceOf(InstructionNotFoundException.class);
    }

    @Test
    void getInstructionAsServiceUsesServiceTokenOnly() {
        server.expect(requestTo("http://instruction.test/api/v1/instructions/I-1"))
                .andExpect(header("Authorization", "Bearer svc-token"))
                .andRespond(withSuccess("{\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));
        assertThat(client.getInstructionAsService("I-1").get("status").asText()).isEqualTo("APPROVED");
        server.verify();
    }
}
