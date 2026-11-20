package com.pulsepass.order;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.pulsepass.purchase.PurchaseService;

@WebMvcTest(OrderController.class)
class OrderControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrderService service;
    @MockitoBean PurchaseService purchases;

    @Test void queryMapsSuccessAndErrors() throws Exception {
        when(service.get(1)).thenReturn(new OrderResponse(1L,2L,3L,4L,1,59900L,OrderStatus.CONFIRMED,OffsetDateTime.parse("2030-01-01T00:00:00Z")));
        when(service.get(99)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        when(service.get(0)).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order ID"));
        mvc.perform(get("/api/v1/orders/1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED")).andExpect(jsonPath("$.data.amountCents").value(59900));
        mvc.perform(get("/api/v1/orders/99")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders/not-a-number")).andExpect(status().isBadRequest());
    }
}
