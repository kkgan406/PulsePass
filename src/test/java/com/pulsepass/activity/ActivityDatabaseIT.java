package com.pulsepass.activity;

import static org.junit.jupiter.api.Assertions.*;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ActivityDatabaseIT {
    @Autowired ActivityService service;
    @Test void insertsFindsAndOrdersUsingMysql() {
        var t = OffsetDateTime.parse("2030-01-01T08:00:00+08:00");
        var request = new CreateActivityRequest("integration test", null, t, t.plusHours(1), t.plusHours(2));
        var first = service.create(request);
        var second = service.create(request);
        assertNotNull(first.id());
        assertEquals("DRAFT", service.get(first.id()).status());
        assertEquals(OffsetDateTime.parse("2030-01-01T00:00:00Z"), service.get(first.id()).saleStartTime());
        var list = service.list(1, 2);
        assertEquals(second.id(), list.get(0).id());
        assertEquals(first.id(), list.get(1).id());
    }
}
