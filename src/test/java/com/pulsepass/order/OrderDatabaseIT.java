package com.pulsepass.order;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OrderDatabaseIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService service;
    long activityId;
    long ticketId;

    @BeforeEach void fixture() {
        String marker = "order-it-" + UUID.randomUUID();
        jdbc.update("INSERT INTO activity(name,sale_start_time,sale_end_time,event_start_time,status,created_at) VALUES (?, '2030-01-01', '2030-01-02', '2030-01-03', 'DRAFT', UTC_TIMESTAMP(3))", marker);
        activityId = jdbc.queryForObject("SELECT id FROM activity WHERE name=?", Long.class, marker);
        jdbc.update("INSERT INTO ticket_type(activity_id,name,price_cents,status,created_at) VALUES (?, ?, 59900, 'ENABLED', UTC_TIMESTAMP(3))", activityId, marker);
        ticketId = jdbc.queryForObject("SELECT id FROM ticket_type WHERE activity_id=? AND name=?", Long.class, activityId, marker);
    }
    @Test void readsDatabaseOrder() {
        jdbc.update("INSERT INTO ticket_order(user_id,activity_id,ticket_type_id,quantity,amount_cents,status,created_at) VALUES (123,?,?,1,59900,'CONFIRMED','2030-01-01 00:00:00.000')", activityId, ticketId);
        Long id = jdbc.queryForObject("SELECT id FROM ticket_order WHERE user_id=123 AND ticket_type_id=?", Long.class, ticketId);
        OrderResponse order = service.get(id);
        assertEquals(OrderStatus.CONFIRMED, order.status());
        assertEquals(59900L, order.amountCents());
        assertEquals(ticketId, order.ticketTypeId());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ticket_order WHERE id=?", Integer.class, id));
    }
    @Test void mysqlEnforcesCheckAndForeignKeys() {
        assertCheck(() -> insert(activityId,ticketId,2,0));
        assertCheck(() -> insert(activityId,ticketId,1,-1));
        assertThrows(DataIntegrityViolationException.class, () -> insert(Long.MAX_VALUE,ticketId,1,0));
        assertThrows(DataIntegrityViolationException.class, () -> insert(activityId,Long.MAX_VALUE,1,0));
    }
    private void insert(long activity, long ticket, int quantity, long amount) {
        jdbc.update("INSERT INTO ticket_order(user_id,activity_id,ticket_type_id,quantity,amount_cents,status,created_at) VALUES (123,?,?,?,?,'PENDING',UTC_TIMESTAMP(3))", activity,ticket,quantity,amount);
    }
    private void assertCheck(Runnable insert) {
        DataAccessException exception = assertThrows(DataAccessException.class, insert::run);
        assertInstanceOf(SQLException.class, exception.getMostSpecificCause());
        assertEquals(3819, ((SQLException) exception.getMostSpecificCause()).getErrorCode());
    }
}
