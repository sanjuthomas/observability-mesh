package com.observabilitymesh.sequence.model;

import jakarta.validation.constraints.NotBlank;

public record NextSecurityEventSequenceRequest(@NotBlank String resourceId) {}
