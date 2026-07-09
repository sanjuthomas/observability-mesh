package com.observabilitymesh.instruction.config;

import com.observabilitymesh.auth.KeycloakLoginClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class ServiceTokenHolder {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenHolder.class);
    private static final long TOKEN_TTL_MS = Duration.ofMinutes(4).toMillis();

    private final KeycloakLoginClient loginClient;
    private final InstructionProperties properties;

    private volatile String token;
    private volatile String sessionId;
    private volatile Instant tokenAcquiredAt;

    public ServiceTokenHolder(KeycloakLoginClient loginClient, InstructionProperties properties) {
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
        if (token != null && !isExpired()) {
            return;
        }
        token = null;
        sessionId = null;
        tokenAcquiredAt = null;
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                KeycloakLoginClient.LoginResponse response = loginClient.login(
                        properties.serviceUserId(),
                        properties.serviceUserPassword());
                this.token = response.sessionToken();
                this.sessionId = response.sessionId();
                this.tokenAcquiredAt = Instant.now();
                log.info("instruction-service authenticated as {} (session_id={})",
                        properties.serviceUserId(), sessionId);
                return;
            } catch (Exception ex) {
                last = ex;
                token = null;
                sessionId = null;
                tokenAcquiredAt = null;
                if (attempt < maxAttempts) {
                    log.warn("instruction-service login attempt {}/{} for {} failed: {} — retrying",
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
        log.error("instruction-service could not authenticate as {} after {} attempts: {} — "
                        + "OBO delegation will be unavailable",
                properties.serviceUserId(), maxAttempts, last == null ? "unknown" : last.getMessage());
    }

    public void ensureLoggedIn() {
        if (token == null || isExpired()) {
            login(3, 1000L);
        }
    }

    private boolean isExpired() {
        return tokenAcquiredAt == null
                || Duration.between(tokenAcquiredAt, Instant.now()).toMillis() >= TOKEN_TTL_MS;
    }

    void expireTokenForTest() {
        tokenAcquiredAt = Instant.EPOCH;
    }
}
