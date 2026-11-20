package com.pulsepass.ticket;

import com.pulsepass.activity.Activity;
import com.pulsepass.activity.ActivityMapper;
import com.pulsepass.activity.ActivityStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketTypeService {
    private final ActivityMapper activities;
    private final TicketTypeMapper tickets;
    private final InventoryMapper inventories;

    public TicketTypeService(ActivityMapper activities, TicketTypeMapper tickets, InventoryMapper inventories) {
        this.activities = activities;
        this.tickets = tickets;
        this.inventories = inventories;
    }

    @Transactional
    public TicketTypeResponse create(long activityId, CreateTicketTypeRequest request) {
        Activity activity = activities.lockById(activityId);
        if (activity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found");
        if (!ActivityStatus.DRAFT.name().equals(activity.status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Activity is not DRAFT");
        }
        if (request.name() == null || request.name().isBlank() || request.name().length() > 100 ||
            request.priceCents() == null || request.priceCents() < 0 ||
            request.totalStock() == null || request.totalStock() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ticket type");
        }
        TicketType ticket = new TicketType();
        ticket.activityId = activityId;
        ticket.name = request.name();
        ticket.priceCents = request.priceCents();
        ticket.status = TicketTypeStatus.ENABLED.name();
        ticket.createdAt = LocalDateTime.now(Clock.systemUTC()).truncatedTo(ChronoUnit.MILLIS);
        tickets.insert(ticket);
        Inventory inventory = new Inventory();
        inventory.ticketTypeId = ticket.id;
        inventory.totalStock = request.totalStock();
        inventory.availableStock = request.totalStock();
        inventories.insert(inventory);
        return new TicketTypeResponse(ticket.id, activityId, ticket.name, ticket.priceCents,
                                      ticket.status, inventory.totalStock, inventory.availableStock);
    }

    public List<TicketTypeResponse> list(long activityId) {
        requireActivity(activityId);
        return tickets.findWithInventory(activityId);
    }

    private Activity requireActivity(long activityId) {
        Activity activity = activities.selectById(activityId);
        if (activity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found");
        return activity;
    }
}
