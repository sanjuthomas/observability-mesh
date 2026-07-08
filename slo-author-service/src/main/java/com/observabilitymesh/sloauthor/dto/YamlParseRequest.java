package com.observabilitymesh.sloauthor.dto;

import jakarta.validation.constraints.NotBlank;

public record YamlParseRequest(
    @NotBlank String yaml
) {}
