package com.pulsepass.purchase;

import com.pulsepass.activity.Activity;
import com.pulsepass.activity.ActivityMapper;
import com.pulsepass.order.OrderMapper;
import com.pulsepass.order.TicketOrder;
import com.pulsepass.ticket.Inventory;
import com.pulsepass.ticket.InventoryMapper;
import com.pulsepass.ticket.TicketType;
import com.pulsepass.ticket.TicketTypeMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseService {
    private final ActivityMapper activities;
    private final TicketTypeMapper tickets;
    private final InventoryMapper inventory;
    private final OrderMapper orders;
    private final Clock clock;

    public PurchaseService(ActivityMapper activities, TicketTypeMapper tickets, InventoryMapper inventory,
                           OrderMapper orders, Clock clock) {
        this.activities=activities; this.tickets=tickets; this.inventory=inventory; this.orders=orders; this.clock=clock;
    }
    @Transactional
    public TicketOrder purchase(PurchaseRequest request) {
        if (request.userId()==null || request.userId()<=0 || request.activityId()==null || request.activityId()<=0 || request.ticketTypeId()==null || request.ticketTypeId()<=0)
            throw problem("INVALID_ARGUMENT", HttpStatus.BAD_REQUEST, "Invalid request");
        Activity activity=activities.selectById(request.activityId());
        if (activity==null) throw problem("ACTIVITY_NOT_FOUND", HttpStatus.NOT_FOUND, "Activity not found");
        if (!"ON_SALE".equals(activity.status)) throw problem("ACTIVITY_NOT_ON_SALE", HttpStatus.CONFLICT, "Activity is not on sale");
        LocalDateTime now=LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        // Phase 0 explicitly includes both endpoints of the sale interval.
        if (now.isBefore(activity.saleStartTime) || now.isAfter(activity.saleEndTime))
            throw problem("SALE_WINDOW_CLOSED", HttpStatus.CONFLICT, "Outside sale period");
        TicketType ticket=tickets.selectById(request.ticketTypeId());
        if (ticket==null) throw problem("TICKET_TYPE_NOT_FOUND", HttpStatus.NOT_FOUND, "Ticket type not found");
        if (!request.activityId().equals(ticket.activityId)) throw problem("INVALID_ARGUMENT", HttpStatus.BAD_REQUEST, "Ticket type does not belong to activity");
        if (!"ENABLED".equals(ticket.status)) throw problem("TICKET_TYPE_DISABLED", HttpStatus.CONFLICT, "Ticket type disabled");
        if (orders.countActive(request.userId(),request.activityId())>0) throw problem("DUPLICATE_ORDER", HttpStatus.CONFLICT, "Active order already exists");
        if (inventory.decrementAvailable(ticket.id)==0) {
            // A concurrent request may have committed an active order while this one waited for stock.
            if (orders.findActiveForUpdate(request.userId(),request.activityId())!=null)
                throw problem("DUPLICATE_ORDER", HttpStatus.CONFLICT, "Active order already exists");
            Long count=inventory.selectCount(new QueryWrapper<Inventory>().eq("ticket_type_id", ticket.id));
            if (count==0) throw problem("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "Inventory unavailable");
            throw problem("OUT_OF_STOCK", HttpStatus.CONFLICT, "Out of stock");
        }
        TicketOrder order=new TicketOrder();
        order.userId=request.userId(); order.activityId=request.activityId(); order.ticketTypeId=ticket.id;
        order.quantity=1; order.amountCents=ticket.priceCents; order.status="CONFIRMED"; order.createdAt=now;
        orders.insert(order);
        return order;
    }
    private PurchaseException problem(String code,HttpStatus status,String message) { return new PurchaseException(code,status,message); }
}
