package com.pulsepass.order;

import com.pulsepass.activity.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import jakarta.validation.Valid;
import com.pulsepass.purchase.PurchaseRequest;
import com.pulsepass.purchase.PurchaseService;
import com.pulsepass.purchase.PurchaseException;

// Local development demo only: authentication and ownership checks are not implemented.
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService service;
    private final PurchaseService purchases;
    public OrderController(OrderService service, PurchaseService purchases) { this.service = service; this.purchases = purchases; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> purchase(@Valid @RequestBody PurchaseRequest request) {
        try {
            return ApiResponse.success(OrderResponse.from(purchases.purchase(request)));
        } catch (DuplicateKeyException e) {
            if (e.getMostSpecificCause().getMessage() != null && e.getMostSpecificCause().getMessage().contains("uq_active_order"))
                throw new PurchaseException("DUPLICATE_ORDER", HttpStatus.CONFLICT, "Active order already exists");
            throw e;
        }
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> get(@PathVariable long orderId) {
        return ApiResponse.success(service.get(orderId));
    }
}
