package com.pulsepass.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OrderServiceTest {
    OrderMapper mapper = mock(OrderMapper.class);
    OrderService service = new OrderService(mapper);

    @Test void mapsOrderStatusAmountAndUtcTime() {
        TicketOrder order = new TicketOrder();
        order.id = 7L; order.userId = 8L; order.activityId = 9L; order.ticketTypeId = 10L;
        order.quantity = 1; order.amountCents = 59900L; order.createdAt = LocalDateTime.parse("2030-01-01T00:00:00");
        for (OrderStatus status : OrderStatus.values()) {
            order.status = status.name();
            when(mapper.selectById(7L)).thenReturn(order);
            var result = service.get(7);
            assertEquals(status, result.status());
            assertEquals(59900L, result.amountCents());
            assertEquals(OffsetDateTime.parse("2030-01-01T00:00:00Z"), result.createdAt());
        }
    }
    @Test void rejectsInvalidAndMissing() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.get(0)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.get(-1)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.get(99)).getStatusCode().value());
        verify(mapper, never()).selectById(0L);
    }
}
