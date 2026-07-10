package com.observabilitymesh.instruction.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ServiceTokenHolderStartup {

    private final ServiceTokenHolder serviceTokenHolder;

    public ServiceTokenHolderStartup(ServiceTokenHolder serviceTokenHolder) {
        this.serviceTokenHolder = serviceTokenHolder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        serviceTokenHolder.login(5, 2000L);
    }
}
