package com.observabilitymesh.harness;

import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.harness.seed.SeedFile;
import com.observabilitymesh.harness.seed.SeedUser;

import java.util.List;
import java.util.Map;

public final class HarnessTestFixtures {

    public static final Subject ADMIN = new Subject(
            "admin-001", "Admin", "User", "Platform Admin", null,
            List.of("PLATFORM_ADMIN"), List.of(), null, List.of(), null, List.of());

    public static final SeedFile SAMPLE_SEED = new SeedFile(
            Map.of("password", "Password1!", "email_domain", "ssi.local"),
            List.of(
                    new SeedUser(
                            "mo-100", "Sarah", "Chen", "Analyst",
                            List.of("INSTRUCTION_CREATOR"), List.of("MIDDLE_OFFICE"),
                            null, "mo-050", List.of()),
                    new SeedUser(
                            "ficc-300", "Elena", "Vasquez", "Vice President",
                            List.of("INSTRUCTION_APPROVER"), List.of(),
                            "FICC", null, List.of()),
                    new SeedUser(
                            "pay-101", "Amy", "Nguyen", "Analyst",
                            List.of("PAYMENT_CREATOR"), List.of("MIDDLE_OFFICE", "UP_TO_100_MILLION_CLUB"),
                            null, null, List.of("FICC", "FX")),
                    new SeedUser(
                            "fo-ficc-101", "Tom", "Lee", "Analyst",
                            List.of("PAYMENT_CREATOR"), List.of(),
                            "FICC", null, List.of()),
                    new SeedUser(
                            "pay-201", "Rita", "Singh", "Vice President",
                            List.of("FUNDING_APPROVER"), List.of("MIDDLE_OFFICE", "UP_TO_1_BILLION_CLUB"),
                            null, null, List.of("FICC", "FX"))));

    private HarnessTestFixtures() {
    }
}
