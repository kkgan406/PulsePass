package com.pulsepass.activity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ActivityServiceTest {
    ActivityMapper mapper = mock(ActivityMapper.class);
    ActivityService service = new ActivityService(mapper);
    OffsetDateTime t = OffsetDateTime.parse("2030-01-01T00:00:00Z");
    CreateActivityRequest request(OffsetDateTime start, OffsetDateTime end, OffsetDateTime event) {
        return new CreateActivityRequest("Concert", null, start, end, event);
    }
    @Test void createSetsDraftAndRejectsBadTimes() {
        var result = service.create(request(t, t.plusHours(1), t.plusHours(2)));
        assertEquals("DRAFT", result.status());
        assertThrows(ResponseStatusException.class, () -> service.create(request(t, t, t.plusHours(1))));
        assertThrows(ResponseStatusException.class, () -> service.create(request(t, t.plusHours(2), t.plusHours(1))));
    }
    @Test void missingAndPagination() {
        when(mapper.selectById(anyLong())).thenReturn(null);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.get(42)).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> service.list(0, 10));
        assertThrows(ResponseStatusException.class, () -> service.list(1, 101));
        when(mapper.findPage(10, 0)).thenReturn(List.of());
        assertEquals(List.of(), service.list(1, 10));
    }
}
