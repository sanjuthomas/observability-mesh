package com.observabilitymesh.instruction.config;

import com.observabilitymesh.auth.KeycloakLoginClient;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ServiceTokenHolderStartupTest {

    @Mock ServiceTokenHolder serviceTokenHolder;
    @Mock ApplicationReadyEvent event;

    @Test
    void onReadyInvokesLogin() {
        ServiceTokenHolderStartup startup = new ServiceTokenHolderStartup(serviceTokenHolder);
        startup.onReady();
        verify(serviceTokenHolder).login(5, 2000L);
    }
}
