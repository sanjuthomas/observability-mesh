package com.srecatalog.instruction.config;

import com.srecatalog.auth.KeycloakLoginClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTokenHolderIdempotentTest {

    @Mock KeycloakLoginClient loginClient;

    @Test
    void loginSkipsWhenTokenAlreadyPresent() {
        InstructionProperties properties = new InstructionProperties(
                "instructions", "security_events", "instruction_service",
                "svc-instruction", "Password1!", "", "", "", 200);
        ServiceTokenHolder holder = new ServiceTokenHolder(loginClient, properties);
        when(loginClient.login("svc-instruction", "Password1!"))
                .thenReturn(new KeycloakLoginClient.LoginResponse("svc-instruction", "token", "sess"));
        holder.login(1, 0);
        holder.login(1, 0);
        verify(loginClient).login("svc-instruction", "Password1!");
        assertThat(holder.token()).isEqualTo("token");
    }
}
