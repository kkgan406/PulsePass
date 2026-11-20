package com.pulsepass;

import org.junit.jupiter.api.Test;
import com.pulsepass.activity.ActivityMapper;
import com.pulsepass.ticket.TicketTypeService;
import com.pulsepass.order.OrderService;
import com.pulsepass.purchase.PurchaseService;
import com.pulsepass.activity.ActivityPublicationService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
class PulsePassApplicationTests {
    @MockitoBean ActivityMapper activityMapper;
    @MockitoBean TicketTypeService ticketTypeService;
    @MockitoBean OrderService orderService;
    @MockitoBean PurchaseService purchaseService;
    @MockitoBean ActivityPublicationService activityPublicationService;
    @Test
    void contextLoads() {
    }
}
