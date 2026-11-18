package com.pulsepass.ticket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.pulsepass.activity.Activity;
import com.pulsepass.activity.ActivityMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TicketTypeServiceTest {
    ActivityMapper activities = mock(ActivityMapper.class);
    TicketTypeMapper tickets = mock(TicketTypeMapper.class);
    InventoryMapper inventories = mock(InventoryMapper.class);
    TicketTypeService service = new TicketTypeService(activities, tickets, inventories);

    @Test void createsEnabledTicketAndMatchingInventory() {
        Activity activity = new Activity(); activity.status = "DRAFT";
        when(activities.selectById(7L)).thenReturn(activity);
        when(tickets.insert(any(TicketType.class))).thenAnswer(invocation -> {
            TicketType ticket = invocation.getArgument(0); ticket.id = 42L; return 1;
        });
        var result = service.create(7L, new CreateTicketTypeRequest("VIP", 59900L, 200));
        assertEquals(42L, result.ticketTypeId());
        assertEquals("ENABLED", result.status());
        assertEquals(200, result.totalStock());
        assertEquals(200, result.availableStock());
        verify(inventories).insert(org.mockito.ArgumentMatchers.<Inventory>argThat(i -> i.ticketTypeId == 42 && i.totalStock == 200 && i.availableStock == 200));
    }
    @Test void checksMissingActivityAndDraft() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.list(123)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.create(123, new CreateTicketTypeRequest("VIP", 1L, 1))).getStatusCode().value());
        Activity activity = new Activity(); activity.status = "ON_SALE";
        when(activities.selectById(7L)).thenReturn(activity);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.create(7, new CreateTicketTypeRequest("VIP", 1L, 1))).getStatusCode().value());
        verifyNoInteractions(tickets, inventories);
    }
}
