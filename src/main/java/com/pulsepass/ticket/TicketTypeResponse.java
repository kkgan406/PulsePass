package com.pulsepass.ticket;

public record TicketTypeResponse(Long ticketTypeId, Long activityId, String name, Long priceCents,
                                 String status, Integer totalStock, Integer availableStock) { }
