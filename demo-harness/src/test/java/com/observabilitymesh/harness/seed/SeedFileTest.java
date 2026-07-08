package com.observabilitymesh.harness.seed;

import com.observabilitymesh.harness.HarnessTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedFileTest {

    @Test
    void normalizesNullCollections() {
        SeedFile seed = new SeedFile(null, null);

        assertThat(seed.defaults()).isEmpty();
        assertThat(seed.users()).isEmpty();
    }

    @Test
    void userByIdThrowsForUnknownUser() {
        assertThatThrownBy(() -> HarnessTestFixtures.SAMPLE_SEED.userById("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void userByIdFindsExistingUser() {
        assertThat(HarnessTestFixtures.SAMPLE_SEED.userById("mo-100").userId()).isEqualTo("mo-100");
    }
}
