package com.observabilitymesh.payment.config;

import com.observabilitymesh.auth.KeycloakLoginClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceIdentityTest {

    @Mock KeycloakLoginClient loginClient;

    @Test
    void loginCachesToken() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac-scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", "", "", "", 200);
        ServiceIdentity identity = new ServiceIdentity(loginClient, properties);
        when(loginClient.login("svc-payment", "Password1!"))
                .thenReturn(new KeycloakLoginClient.LoginResponse("svc-payment", "token-1", "sess-1"));

        identity.login(1, 0);
        assertThat(identity.token()).isEqualTo("token-1");
        assertThat(identity.sessionId()).isEqualTo("sess-1");
    }

    @Test
    void loginSkipsWhenAlreadyAuthenticated() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac-scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", "", "", "", 200);
        ServiceIdentity identity = new ServiceIdentity(loginClient, properties);
        when(loginClient.login("svc-payment", "Password1!"))
                .thenReturn(new KeycloakLoginClient.LoginResponse("svc-payment", "token-1", "sess-1"));

        identity.login(1, 0);
        identity.login(1, 0);
        verify(loginClient, times(1)).login("svc-payment", "Password1!");
    }

    @Test
    void loginGivesUpAfterMaxAttempts() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac-scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", "", "", "", 200);
        ServiceIdentity identity = new ServiceIdentity(loginClient, properties);
        when(loginClient.login("svc-payment", "Password1!"))
                .thenThrow(new IllegalStateException("keycloak down"));

        identity.login(2, 0);
        assertThat(identity.token()).isNull();
    }
}
