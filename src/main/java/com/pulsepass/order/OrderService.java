package com.pulsepass.order;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private final OrderMapper orders;
    public OrderService(OrderMapper orders) { this.orders = orders; }

    public OrderResponse get(long orderId) {
        if (orderId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order ID");
        TicketOrder order = orders.selectById(orderId);
        if (order == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        return OrderResponse.from(order);
    }
}
