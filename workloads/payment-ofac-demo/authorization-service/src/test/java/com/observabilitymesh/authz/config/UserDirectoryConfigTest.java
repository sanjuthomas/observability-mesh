package com.observabilitymesh.authz.config;

import com.observabilitymesh.authz.directory.UserDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class UserDirectoryConfigTest {

    @Test
    void loadsUserDirectoryFromClasspath() throws Exception {
        UserDirectoryConfig config = new UserDirectoryConfig();
        AuthzProperties properties = new AuthzProperties(
                "http://opa:9181", "classpath:users.yaml", "COMPLIANCE_ANALYST");
        UserDirectory directory = config.userDirectory(properties, new DefaultResourceLoader());
        assertThat(directory.allUsers()).isNotEmpty();
    }

    @Test
    void prefixesBareFilePathWithFileScheme() throws Exception {
        UserDirectoryConfig config = new UserDirectoryConfig();
        var loader = new DefaultResourceLoader();
        var resource = loader.getResource("classpath:users.yaml");
        AuthzProperties properties = new AuthzProperties(
                "http://opa:9181", resource.getFile().getAbsolutePath(), "COMPLIANCE_ANALYST");
        UserDirectory directory = config.userDirectory(properties, loader);
        assertThat(directory.emailDomain()).isEqualTo("ssi.local");
    }
}
