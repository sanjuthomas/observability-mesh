package com.srecatalog.sloprovisioner.config;

import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MongoConfigTest {

    @Mock MongoClient mongoClient;

    @Test
    void createsMongoTemplate() {
        MongoConfig config = new MongoConfig();
        MongoTemplate template = config.sloProvisionerMongoTemplate(mongoClient, "open-slo");
        assertThat(template).isNotNull();
    }
}
