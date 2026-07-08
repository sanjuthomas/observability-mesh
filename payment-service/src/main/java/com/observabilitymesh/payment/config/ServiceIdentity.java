package com.observabilitymesh.payment.config;

import com.observabilitymesh.auth.KeycloakLoginClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ServiceIdentity {

    private static final Logger log = LoggerFactory.getLogger(ServiceIdentity.class);

    private final KeycloakLoginClient loginClient;
    private final PaymentProperties properties;

    private volatile String token;
    private volatile String sessionId;

    public ServiceIdentity(KeycloakLoginClient loginClient, PaymentProperties properties) {
        this.loginClient = loginClient;
        this.properties = properties;
    }

    public String token() {
        return token;
    }

    public String sessionId() {
        return sessionId;
    }

    public synchronized void login(int maxAttempts, long retryDelayMs) {
        if (token != null) {
            return;
        }
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                KeycloakLoginClient.LoginResponse response = loginClient.login(
                        properties.serviceUserId(),
                        properties.serviceUserPassword());
                this.token = response.sessionToken();
                this.sessionId = response.sessionId();
                log.info("payment-service authenticated as {} (session_id={})",
                        properties.serviceUserId(), sessionId);
                return;
            } catch (Exception ex) {
                last = ex;
                token = null;
                sessionId = null;
                if (attempt < maxAttempts) {
                    log.warn("payment-service login attempt {}/{} for {} failed: {} — retrying",
                            attempt, maxAttempts, properties.serviceUserId(), ex.getMessage());
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("payment-service could not authenticate as {} after {} attempts: {} — "
                        + "OBO delegation will be unavailable",
                properties.serviceUserId(), maxAttempts, last == null ? "unknown" : last.getMessage());
    }

    public void ensureLoggedIn() {
        if (token == null) {
            login(3, 1000L);
        }
    }
}
