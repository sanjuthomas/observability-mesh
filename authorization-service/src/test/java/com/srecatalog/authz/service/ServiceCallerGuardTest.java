package com.srecatalog.authz.service;

import com.srecatalog.common.model.Subject;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceCallerGuardTest {

    @Test
    void allowsConfiguredServiceUsers() {
        ServiceCallerGuard guard = new ServiceCallerGuard("svc-instruction,svc-payment");
        Subject caller = new Subject(
                "svc-instruction", null, null, "Service", null,
                List.of("SERVICE"), List.of(), null, List.of(), null, List.of());

        assertThatCode(() -> guard.requireAuthorizedService(caller)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownServiceUsers() {
        ServiceCallerGuard guard = new ServiceCallerGuard("svc-instruction");
        Subject caller = new Subject(
                "svc-payment", null, null, "Service", null,
                List.of("SERVICE"), List.of(), null, List.of(), null, List.of());

        assertThatThrownBy(() -> guard.requireAuthorizedService(caller))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not authorized");
    }

    @Test
    void rejectsAllCallersWhenAllowListBlank() {
        ServiceCallerGuard guard = new ServiceCallerGuard("");
        Subject caller = new Subject(
                "svc-instruction", null, null, "Service", null,
                List.of("SERVICE"), List.of(), null, List.of(), null, List.of());

        assertThatThrownBy(() -> guard.requireAuthorizedService(caller))
                .isInstanceOf(ResponseStatusException.class);
    }
}
