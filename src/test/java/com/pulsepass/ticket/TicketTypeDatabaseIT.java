package com.pulsepass.ticket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.UUID;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
class TicketTypeDatabaseIT {
    @Autowired TicketTypeService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean InventoryMapper inventoryMapper;
    long activityId;
    String marker;

    @BeforeEach void createFixture() {
        marker = "ticket-it-" + UUID.randomUUID();
        jdbc.update("INSERT INTO activity(name,sale_start_time,sale_end_time,event_start_time,status,created_at) VALUES (?, '2030-01-01', '2030-01-02', '2030-01-03', 'DRAFT', UTC_TIMESTAMP(3))", marker);
        activityId = jdbc.queryForObject("SELECT id FROM activity WHERE name=?", Long.class, marker);
    }
    @AfterEach void cleanupOwnFixture() {
        jdbc.update("DELETE i FROM inventory i JOIN ticket_type t ON i.ticket_type_id=t.id WHERE t.activity_id=?", activityId);
        jdbc.update("DELETE FROM ticket_type WHERE activity_id=?", activityId);
        jdbc.update("DELETE FROM activity WHERE id=? AND name=?", activityId, marker);
    }
    @Test void createMultipleListAndPersist() {
        assertTrue(service.list(activityId).isEmpty());
        var first = service.create(activityId, new CreateTicketTypeRequest("Regular", 0L, 0));
        var second = service.create(activityId, new CreateTicketTypeRequest("VIP", 59900L, 200));
        assertEquals("ENABLED", first.status());
        assertEquals(200, second.totalStock()); assertEquals(200, second.availableStock());
        var result = service.list(activityId);
        assertEquals(2, result.size());
        assertEquals(first.ticketTypeId(), result.get(0).ticketTypeId());
        assertEquals(second.ticketTypeId(), result.get(1).ticketTypeId());
        assertEquals(200, result.get(1).availableStock());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ticket_type t JOIN inventory i ON i.ticket_type_id=t.id WHERE t.id=?", Integer.class, second.ticketTypeId()));
    }
    @Test void databaseRejectsBrokenConstraints() {
        var ticket = service.create(activityId, new CreateTicketTypeRequest("VIP", 1L, 1));
        long id = ticket.ticketTypeId();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO inventory(ticket_type_id,total_stock,available_stock) VALUES (?,1,1)", id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO inventory(ticket_type_id,total_stock,available_stock) VALUES (9223372036854775807,1,1)"));
        assertCheckViolation(() -> jdbc.update("UPDATE inventory SET total_stock=-1 WHERE ticket_type_id=?", id));
        assertCheckViolation(() -> jdbc.update("UPDATE inventory SET available_stock=-1 WHERE ticket_type_id=?", id));
        assertCheckViolation(() -> jdbc.update("UPDATE inventory SET available_stock=2 WHERE ticket_type_id=?", id));
        assertCheckViolation(() -> jdbc.update("UPDATE ticket_type SET price_cents=-1 WHERE id=?", id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE ticket_type SET activity_id=9223372036854775807 WHERE id=?", id));
    }
    private void assertCheckViolation(Runnable statement) {
        DataAccessException exception = assertThrows(DataAccessException.class, statement::run);
        assertInstanceOf(SQLException.class, exception.getMostSpecificCause());
        assertEquals(3819, ((SQLException) exception.getMostSpecificCause()).getErrorCode());
    }
    @Test void inventoryFailureRollsBackTicketOutsideTestTransaction() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            Inventory attempted = call.getArgument(0);
            assertNotNull(attempted.ticketTypeId);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ticket_type WHERE id=?", Integer.class, attempted.ticketTypeId));
            throw new IllegalStateException("injected inventory failure");
        }).when(inventoryMapper).insert(any(Inventory.class));
        assertThrows(IllegalStateException.class, () -> service.create(activityId, new CreateTicketTypeRequest("rollback-marker", 1L, 1)));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ticket_type WHERE activity_id=? AND name='rollback-marker'", Integer.class, activityId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inventory i JOIN ticket_type t ON i.ticket_type_id=t.id WHERE t.activity_id=?", Integer.class, activityId));
    }
}
