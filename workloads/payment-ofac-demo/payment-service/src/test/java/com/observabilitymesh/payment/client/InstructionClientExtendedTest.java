package com.observabilitymesh.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.payment.config.ServiceIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstructionClientExtendedTest {

    @Mock ServiceIdentity serviceIdentity;

    private MockRestServiceServer server;
    private InstructionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new InstructionClient(builder, serviceIdentity, new ObjectMapper(), "http://instruction.test/");
        when(serviceIdentity.token()).thenReturn("Bearer svc-token");
        when(serviceIdentity.sessionId()).thenReturn("svc-session");
    }

    @Test
    void getInstructionNotFound() {
        server.expect(requestTo("http://instruction.test/api/v1/instructions/missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.getInstruction("missing", "user-token", "sess"))
                .isInstanceOf(InstructionNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void getInstructionServerError() {
        server.expect(requestTo("http://instruction.test/api/v1/instructions/I-1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));
        assertThatThrownBy(() -> client.getInstruction("I-1", "user-token", "sess"))
                .isInstanceOf(InstructionClientException.class);
    }

    @Test
    void getInstructionMalformedJson() {
        server.expect(requestTo("http://instruction.test/api/v1/instructions/I-1"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getInstruction("I-1", "user-token", "sess"))
                .isInstanceOf(InstructionClientException.class);
    }

    @Test
    void markUsedTruncatesLongPaymentReference() {
        String longId = "P-" + "x".repeat(40);
        server.expect(requestTo("http://instruction.test/api/v1/instructions/I-1/use"))
                .andExpect(jsonPath("$.payment_reference").value(longId))
                .andExpect(jsonPath("$.end_to_end_identification").value(longId.substring(0, 35)))
                .andRespond(withSuccess("{\"status\":\"USED\"}", MediaType.APPLICATION_JSON));
        assertThat(client.markUsed("I-1", longId, "user-token", "sess").get("status").asText())
                .isEqualTo("USED");
    }

    @Test
    void postServerErrorThrowsClientException() {
        server.expect(requestTo("http://instruction.test/api/v1/instructions/I-1/release-use"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("down"));
        assertThatThrownBy(() -> client.releaseUse("I-1", "P-1", "user-token", "sess"))
                .isInstanceOf(InstructionClientException.class);
    }

    @Test
    void oboHeadersIncludeOboSessionWhenPresent() {
        server.expect(requestTo("http://instruction.test/api/v1/instructions/I-1"))
                .andExpect(header("Authorization", "Bearer svc-token"))
                .andExpect(header(RequestSubjectResolver.OBO_HEADER, "user-token"))
                .andExpect(header(RequestSubjectResolver.OBO_SESSION_HEADER, "obo-session"))
                .andRespond(withSuccess("{\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));
        client.getInstruction("I-1", "user-token", "obo-session");
        server.verify();
    }

    @Test
    void oboHeadersSkipBlankOboSession() {
        server.expect(requestTo("http://instruction.test/api/v1/instructions/I-1"))
                .andExpect(header(RequestSubjectResolver.OBO_HEADER, "user-token"))
                .andRespond(withSuccess("{\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));
        client.getInstruction("I-1", "user-token", "   ");
        server.verify();
    }

    @Test
    void getInstructionAsServiceEnsuresLogin() {
        server.expect(requestTo("http://instruction.test/api/v1/instructions/I-1"))
                .andRespond(withSuccess("{\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));
        client.getInstructionAsService("I-1");
        verify(serviceIdentity).ensureLoggedIn();
    }

    @Test
    void serviceHeadersOmitSessionWhenMissing() {
        when(serviceIdentity.sessionId()).thenReturn(null);
        server.expect(requestTo("http://instruction.test/api/v1/instructions/I-1"))
                .andExpect(header("Authorization", "Bearer svc-token"))
                .andRespond(withSuccess("{\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));
        client.getInstructionAsService("I-1");
        server.verify();
    }

    @Test
    void userBearerUsedWhenServiceTokenMissing() {
        when(serviceIdentity.token()).thenReturn(null);
        server.expect(requestTo("http://instruction.test/api/v1/instructions/I-1"))
                .andExpect(header("Authorization", "Bearer user-token"))
                .andRespond(withSuccess("{\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));
        client.getInstruction("I-1", "Bearer user-token", null);
        server.verify();
    }

    @Test
    void instructionClientExceptionWrapsMessage() {
        var ex = new InstructionClientException("failed", new IllegalStateException("root"));
        assertThat(ex.getMessage()).contains("failed");
        assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
    }
}
