package com.observabilitymesh.payment.web;

import com.observabilitymesh.payment.config.ServiceIdentity;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ServiceIdentityStartup {

    private final ServiceIdentity serviceIdentity;

    public ServiceIdentityStartup(ServiceIdentity serviceIdentity) {
        this.serviceIdentity = serviceIdentity;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        serviceIdentity.login(5, 2000L);
    }
}
