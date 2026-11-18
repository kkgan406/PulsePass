package com.pulsepass.activity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record CreateActivityRequest(
    @NotBlank @Size(max = 150) String name,
    String description,
    @NotNull OffsetDateTime saleStartTime,
    @NotNull OffsetDateTime saleEndTime,
    @NotNull OffsetDateTime eventStartTime
) { }
