package com.pulsepass.order;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record OrderResponse(Long id, Long userId, Long activityId, Long ticketTypeId,
                            Integer quantity, Long amountCents, OrderStatus status, OffsetDateTime createdAt) {
    static OrderResponse from(TicketOrder order) {
        return new OrderResponse(order.id, order.userId, order.activityId, order.ticketTypeId,
                order.quantity, order.amountCents, OrderStatus.valueOf(order.status),
                order.createdAt.atOffset(ZoneOffset.UTC));
    }
}
