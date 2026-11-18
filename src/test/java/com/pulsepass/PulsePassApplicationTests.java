package com.pulsepass;

import org.junit.jupiter.api.Test;
import com.pulsepass.activity.ActivityMapper;
import com.pulsepass.ticket.TicketTypeService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
class PulsePassApplicationTests {
    @MockitoBean ActivityMapper activityMapper;
    @MockitoBean TicketTypeService ticketTypeService;
    @Test
    void contextLoads() {
    }
}
