package com.observabilitymesh.instruction.config;

import com.observabilitymesh.auth.KeycloakLoginClient;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTokenHolderTest {

    @Mock KeycloakLoginClient loginClient;

    @Test
    void loginStoresTokenOnSuccess() {
        when(loginClient.login("svc-instruction", "Password1!"))
                .thenReturn(new KeycloakLoginClient.LoginResponse("svc-instruction", "token", "sess-1"));
        ServiceTokenHolder holder = new ServiceTokenHolder(loginClient, InstructionTestFixtures.properties());
        holder.login(1, 0);
        assertThat(holder.token()).isEqualTo("token");
        assertThat(holder.sessionId()).isEqualTo("sess-1");
    }

    @Test
    void ensureLoggedInRetriesWhenTokenMissing() {
        when(loginClient.login("svc-instruction", "Password1!"))
                .thenReturn(new KeycloakLoginClient.LoginResponse("svc-instruction", "token", "sess-1"));
        ServiceTokenHolder holder = new ServiceTokenHolder(loginClient, InstructionTestFixtures.properties());
        holder.ensureLoggedIn();
        assertThat(holder.token()).isNotNull();
    }
}
