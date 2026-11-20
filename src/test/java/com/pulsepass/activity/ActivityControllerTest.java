package com.pulsepass.activity;

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

@WebMvcTest(ActivityController.class)
class ActivityControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ActivityService service;
    @MockitoBean ActivityPublicationService publication;
    @Test void rejectsBlankNameAndProtectedFields() throws Exception {
        mvc.perform(post("/api/v1/activities").contentType(MediaType.APPLICATION_JSON).content("""
          {"name":" ","saleStartTime":"2030-01-01T00:00:00Z","saleEndTime":"2030-01-02T00:00:00Z","eventStartTime":"2030-01-03T00:00:00Z"}
          """)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/activities").contentType(MediaType.APPLICATION_JSON).content("""
          {"name":"x","status":"CONFIRMED","saleStartTime":"2030-01-01T00:00:00Z","saleEndTime":"2030-01-02T00:00:00Z","eventStartTime":"2030-01-03T00:00:00Z"}
          """)).andExpect(status().isBadRequest());
    }
    @Test void defaultPaginationAndBadParameters() throws Exception {
        when(service.list(1, 10)).thenReturn(List.of());
        mvc.perform(get("/api/v1/activities")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK"));
        verify(service).list(1, 10);
        mvc.perform(get("/api/v1/activities?page=abc")).andExpect(status().isBadRequest());
    }
}
