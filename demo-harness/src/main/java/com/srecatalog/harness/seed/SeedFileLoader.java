package com.srecatalog.harness.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.srecatalog.harness.config.HarnessProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class SeedFileLoader {

    private final HarnessProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public SeedFileLoader(HarnessProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    public SeedFile load() {
        Resource resource = resourceLoader.getResource(properties.usersFile());
        try (InputStream input = resource.getInputStream()) {
            SeedFileRoot root = yamlMapper.readValue(input, SeedFileRoot.class);
            return new SeedFile(root.defaults(), root.users());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load users seed from " + properties.usersFile(), ex);
        }
    }
}
