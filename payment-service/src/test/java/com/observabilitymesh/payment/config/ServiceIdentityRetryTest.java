package com.observabilitymesh.payment.config;

import com.observabilitymesh.auth.KeycloakLoginClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceIdentityRetryTest {

    @Mock KeycloakLoginClient loginClient;

    @Test
    void loginRetriesUntilSuccess() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac-scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", "", "", "", 200);
        ServiceIdentity identity = new ServiceIdentity(loginClient, properties);
        when(loginClient.login("svc-payment", "Password1!"))
                .thenThrow(new IllegalArgumentException("not ready"))
                .thenReturn(new KeycloakLoginClient.LoginResponse("svc-payment", "token-2", "sess-2"));

        identity.login(2, 0);
        assertThat(identity.token()).isEqualTo("token-2");
    }

    @Test
    void ensureLoggedInTriggersLoginWhenMissing() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac-scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", "", "", "", 200);
        ServiceIdentity identity = new ServiceIdentity(loginClient, properties);
        when(loginClient.login("svc-payment", "Password1!"))
                .thenReturn(new KeycloakLoginClient.LoginResponse("svc-payment", "token-3", "sess-3"));
        identity.ensureLoggedIn();
        assertThat(identity.token()).isEqualTo("token-3");
    }
}
