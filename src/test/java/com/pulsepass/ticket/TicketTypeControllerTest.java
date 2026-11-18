package com.pulsepass.ticket;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TicketTypeController.class)
class TicketTypeControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TicketTypeService service;

    @Test void validatesRequestAndRejectsProtectedFields() throws Exception {
        for (String body : List.of("{\"name\":\" \",\"priceCents\":0,\"totalStock\":0}",
                "{\"name\":\"VIP\",\"priceCents\":-1,\"totalStock\":0}",
                "{\"name\":\"VIP\",\"priceCents\":0,\"totalStock\":-1}",
                "{\"name\":\"VIP\",\"priceCents\":1.5,\"totalStock\":1}",
                "{\"name\":\"VIP\",\"priceCents\":1,\"totalStock\":1.5}",
                "{\"name\":\"VIP\",\"priceCents\":1,\"totalStock\":1,\"ticketTypeId\":99}",
                "{\"name\":\"VIP\",\"priceCents\":0,\"totalStock\":1,\"availableStock\":1}",
                "{\"name\":\"VIP\",\"priceCents\":0,\"totalStock\":1,\"status\":\"ENABLED\"}")) {
            mvc.perform(post("/api/v1/activities/1/ticket-types").contentType(MediaType.APPLICATION_JSON).content(body))
               .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
