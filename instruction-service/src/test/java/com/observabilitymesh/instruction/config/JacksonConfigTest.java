package com.observabilitymesh.instruction.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    @Test
    void instructionObjectMapperUsesSnakeCase() {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper = config.instructionObjectMapper(new ObjectMapper());
        var tree = mapper.valueToTree(InstructionTestFixtures.sampleInstruction("I-1"));
        assertThat(tree.has("instruction_id")).isTrue();
        assertThat(tree.has("owning_lob")).isTrue();
    }
}
