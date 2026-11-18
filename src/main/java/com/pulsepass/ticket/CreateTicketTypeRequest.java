package com.pulsepass.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateTicketTypeRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull @PositiveOrZero Long priceCents,
    @NotNull @PositiveOrZero Integer totalStock
) { }
