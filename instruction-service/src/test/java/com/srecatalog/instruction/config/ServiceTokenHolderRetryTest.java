package com.srecatalog.instruction.config;

import com.srecatalog.auth.KeycloakLoginClient;
import com.srecatalog.instruction.InstructionTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTokenHolderRetryTest {

    @Mock KeycloakLoginClient loginClient;

    @Test
    void loginSurvivesTransientFailure() {
        when(loginClient.login("svc-instruction", "Password1!"))
                .thenThrow(new RuntimeException("keycloak down"))
                .thenReturn(new KeycloakLoginClient.LoginResponse("svc-instruction", "token", "sess"));
        ServiceTokenHolder holder = new ServiceTokenHolder(loginClient, InstructionTestFixtures.properties());
        holder.login(2, 1);
        assertThat(holder.token()).isEqualTo("token");
    }

    @Test
    void loginLeavesTokenNullAfterExhaustedRetries() {
        when(loginClient.login("svc-instruction", "Password1!"))
                .thenThrow(new RuntimeException("keycloak down"));
        ServiceTokenHolder holder = new ServiceTokenHolder(loginClient, InstructionTestFixtures.properties());
        holder.login(1, 1);
        assertThat(holder.token()).isNull();
    }
}
