package com.pulsepass.purchase;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PurchaseRequest(@NotNull @Positive Long userId, @NotNull @Positive Long activityId,
                              @NotNull @Positive Long ticketTypeId) { }
