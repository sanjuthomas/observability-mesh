package com.srecatalog.sequence.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record NextSequenceRequest(
        @NotBlank String businessDate,
        @NotBlank String owningLob,
        @NotBlank @Pattern(regexp = "I|P") String entityType
) {}
