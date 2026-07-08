package com.srecatalog.payment.config;

import com.srecatalog.payment.web.ServiceIdentityStartup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ServiceIdentityStartupTest {

    @Mock ServiceIdentity serviceIdentity;

    @Test
    void onReadyLogsInWithConfiguredRetries() {
        new ServiceIdentityStartup(serviceIdentity).onReady();
        verify(serviceIdentity).login(5, 2000L);
    }
}
