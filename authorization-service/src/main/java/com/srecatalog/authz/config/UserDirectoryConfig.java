package com.srecatalog.authz.config;

import com.srecatalog.authz.directory.UserDirectory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;

@Configuration
public class UserDirectoryConfig {

    @Bean
    UserDirectory userDirectory(AuthzProperties properties, ResourceLoader resourceLoader) throws IOException {
        String location = properties.usersFile();
        if (!location.startsWith("classpath:") && !location.startsWith("file:")) {
            location = "file:" + location;
        }
        try (var inputStream = resourceLoader.getResource(location).getInputStream()) {
            return UserDirectory.load(inputStream);
        }
    }
}
