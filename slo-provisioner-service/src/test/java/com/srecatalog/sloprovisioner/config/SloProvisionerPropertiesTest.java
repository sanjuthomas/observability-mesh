package com.srecatalog.sloprovisioner.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SloProvisionerPropertiesTest {

    @Test
    void datasourceNameSetTrimsSingleValue() {
        SloProvisionerProperties properties = new SloProvisionerProperties(
                60_000, "service-level-objectives", "slo-provision-state",
                "/rules", "_archive", "", "sloth", "/work", "payment-prometheus");
        assertThat(properties.datasourceNameSet()).containsExactly("payment-prometheus");
    }

    @Test
    void datasourceNameSetSplitsCsv() {
        SloProvisionerProperties properties = new SloProvisionerProperties(
                60_000, "service-level-objectives", "slo-provision-state",
                "/rules", "_archive", "", "sloth", "/work", " payment-prometheus , other ");
        assertThat(properties.datasourceNameSet()).containsExactlyInAnyOrder("payment-prometheus", "other");
    }

    @Test
    void datasourceNameSetEmptyWhenBlank() {
        SloProvisionerProperties properties = new SloProvisionerProperties(
                60_000, "service-level-objectives", "slo-provision-state",
                "/rules", "_archive", "", "sloth", "/work", "  ");
        assertThat(properties.datasourceNameSet()).isEmpty();
    }
}
