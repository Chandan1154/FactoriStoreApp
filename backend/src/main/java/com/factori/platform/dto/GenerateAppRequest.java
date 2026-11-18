package com.factori.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateAppRequest(
        @NotBlank @Size(max = 64) String storeName,
        @NotBlank @Size(max = 2048) String storeUrl) {
}
