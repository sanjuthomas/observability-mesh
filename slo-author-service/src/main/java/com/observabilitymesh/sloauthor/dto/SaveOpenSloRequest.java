package com.observabilitymesh.sloauthor.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record SaveOpenSloRequest(
    @NotNull @NotEmpty Map<String, Object> content
) {}
