package com.pulsepass.purchase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.pulsepass.activity.ActivityPublicationService;
import com.pulsepass.order.OrderMapper;
import com.pulsepass.order.TicketOrder;
import com.pulsepass.ticket.CreateTicketTypeRequest;
import com.pulsepass.ticket.TicketTypeService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
class PurchaseDatabaseIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired PurchaseService purchases;
    @Autowired TicketTypeService ticketService;
    @Autowired ActivityPublicationService publication;
    @MockitoBean Clock clock;
    @MockitoSpyBean OrderMapper orderMapper;
    long activity, ticket, otherTicket;
    String marker;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2030-01-02T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        marker="purchase-it-"+UUID.randomUUID();
        jdbc.update("INSERT INTO activity(name,sale_start_time,sale_end_time,event_start_time,status,created_at) VALUES (?, '2030-01-01', '2030-01-03', '2030-01-04', 'DRAFT', UTC_TIMESTAMP(3))",marker);
        activity=jdbc.queryForObject("SELECT id FROM activity WHERE name=?",Long.class,marker);
        ticket=ticketService.create(activity,new CreateTicketTypeRequest("test-a",599L,1)).ticketTypeId();
        otherTicket=ticketService.create(activity,new CreateTicketTypeRequest("test-b",100L,5)).ticketTypeId();
        publication.publish(activity);
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM ticket_order WHERE activity_id=?",activity);
        jdbc.update("DELETE i FROM inventory i JOIN ticket_type t ON t.id=i.ticket_type_id WHERE t.activity_id=?",activity);
        jdbc.update("DELETE FROM ticket_type WHERE activity_id=?",activity);
        jdbc.update("DELETE FROM activity WHERE id=? AND name=?",activity,marker);
    }
    PurchaseRequest request(long user,long type) { return new PurchaseRequest(user,activity,type); }
    int stock(long type) { return jdbc.queryForObject("SELECT available_stock FROM inventory WHERE ticket_type_id=?",Integer.class,type); }
    int orders() { return jdbc.queryForObject("SELECT COUNT(*) FROM ticket_order WHERE activity_id=?",Integer.class,activity); }
    @Test void successDuplicateAndZeroStock() {
        TicketOrder order=purchases.purchase(request(11,ticket));
        assertEquals("CONFIRMED",order.status); assertEquals(599L,order.amountCents); assertEquals(1,order.quantity);
        assertEquals("DUPLICATE_ORDER",assertThrows(PurchaseException.class,()->purchases.purchase(request(11,ticket))).code);
        assertEquals("OUT_OF_STOCK",assertThrows(PurchaseException.class,()->purchases.purchase(request(12,ticket))).code);
        assertEquals(0,stock(ticket)); assertEquals(1,orders());
    }
    @Test void validatesActivityWindowTicketAndInventory() {
        assertEquals("ACTIVITY_NOT_FOUND",assertThrows(PurchaseException.class,()->purchases.purchase(new PurchaseRequest(1L,Long.MAX_VALUE,ticket))).code);
        assertEquals("TICKET_TYPE_NOT_FOUND",assertThrows(PurchaseException.class,()->purchases.purchase(request(1,Long.MAX_VALUE))).code);
        assertEquals("INVALID_ARGUMENT",assertThrows(PurchaseException.class,()->purchases.purchase(new PurchaseRequest(0L,activity,ticket))).code);
        when(clock.instant()).thenReturn(Instant.parse("2029-12-31T00:00:00Z"));
        assertEquals("SALE_WINDOW_CLOSED",assertThrows(PurchaseException.class,()->purchases.purchase(request(1,ticket))).code);
        when(clock.instant()).thenReturn(Instant.parse("2030-01-04T00:00:00Z"));
        assertEquals("SALE_WINDOW_CLOSED",assertThrows(PurchaseException.class,()->purchases.purchase(request(1,ticket))).code);
        when(clock.instant()).thenReturn(Instant.parse("2030-01-02T00:00:00Z"));
        jdbc.update("UPDATE ticket_type SET status='DISABLED' WHERE id=?",ticket);
        assertEquals("TICKET_TYPE_DISABLED",assertThrows(PurchaseException.class,()->purchases.purchase(request(1,ticket))).code);
        jdbc.update("UPDATE ticket_type SET status='ENABLED' WHERE id=?",ticket);
        jdbc.update("DELETE FROM inventory WHERE ticket_type_id=?",ticket);
        assertEquals("INTERNAL_ERROR",assertThrows(PurchaseException.class,()->purchases.purchase(request(1,ticket))).code);
        assertEquals(0,orders());
    }
    @Test void orderInsertFailureRollsBackStock() {
        doThrow(new IllegalStateException("injected failure")).when(orderMapper).insert(any(TicketOrder.class));
        assertThrows(IllegalStateException.class,()->purchases.purchase(request(1,ticket)));
        assertEquals(1,stock(ticket)); assertEquals(0,orders());
    }
    @Test void twoUsersCompeteForLastTicket() throws Exception {
        var results=simultaneous(request(1,ticket),request(2,ticket));
        assertEquals(1,results.stream().filter(x->x.equals("OK")).count());
        assertEquals(1,results.stream().filter(x->x.equals("OUT_OF_STOCK")).count());
        assertEquals(0,stock(ticket)); assertEquals(1,orders());
    }
    @Test void sameUserConcurrentAndCancelledHistory() throws Exception {
        var results=simultaneous(request(1,otherTicket),request(1,otherTicket));
        assertEquals(1,results.stream().filter(x->x.equals("OK")).count());
        assertEquals(1,results.stream().filter(x->x.equals("DUPLICATE_ORDER")).count());
        assertEquals(4,stock(otherTicket)); assertEquals(1,orders());
        jdbc.update("UPDATE ticket_order SET status='CANCELLED' WHERE activity_id=?",activity);
        purchases.purchase(request(1,otherTicket));
        assertEquals(2,orders()); assertEquals(3,stock(otherTicket));
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE ticket_order SET status='bogus' WHERE activity_id=?",activity));
    }
    @Test void sameUserCompetesForLastTicket() throws Exception {
        var results=simultaneous(request(1,ticket),request(1,ticket));
        assertEquals(1,results.stream().filter(x->x.equals("OK")).count());
        assertEquals(1,results.stream().filter(x->x.equals("DUPLICATE_ORDER")).count());
        assertEquals(0,stock(ticket)); assertEquals(1,orders());
    }
    @Test void uniqueIndexRejectsDuplicateActiveOrder() {
        purchases.purchase(request(1,otherTicket));
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->jdbc.update(
            "INSERT INTO ticket_order(user_id,activity_id,ticket_type_id,quantity,amount_cents,status,created_at) VALUES (1,?,?,1,1,'PENDING',UTC_TIMESTAMP(3))",activity,otherTicket));
        assertEquals(1,orders()); assertEquals(4,stock(otherTicket));
    }
    @Test void saleWindowIncludesBothEndpoints() {
        when(clock.instant()).thenReturn(Instant.parse("2030-01-01T00:00:00Z"));
        purchases.purchase(request(1,otherTicket));
        when(clock.instant()).thenReturn(Instant.parse("2030-01-03T00:00:00Z"));
        purchases.purchase(request(2,otherTicket));
        assertEquals(2,orders()); assertEquals(3,stock(otherTicket));
    }
    @Test void draftActivityAndMismatchedTicket() {
        String another=marker+"-other";
        jdbc.update("INSERT INTO activity(name,sale_start_time,sale_end_time,event_start_time,status,created_at) VALUES (?, '2030-01-01', '2030-01-03', '2030-01-04', 'DRAFT', UTC_TIMESTAMP(3))",another);
        long other=jdbc.queryForObject("SELECT id FROM activity WHERE name=?",Long.class,another);
        try {
            long foreign=ticketService.create(other,new CreateTicketTypeRequest("foreign",1L,1)).ticketTypeId();
            assertEquals("ACTIVITY_NOT_ON_SALE",assertThrows(PurchaseException.class,()->purchases.purchase(new PurchaseRequest(1L,other,foreign))).code);
            assertEquals("INVALID_ARGUMENT",assertThrows(PurchaseException.class,()->purchases.purchase(new PurchaseRequest(1L,activity,foreign))).code);
        } finally {
            jdbc.update("DELETE i FROM inventory i JOIN ticket_type t ON t.id=i.ticket_type_id WHERE t.activity_id=?",other);
            jdbc.update("DELETE FROM ticket_type WHERE activity_id=?",other);
            jdbc.update("DELETE FROM activity WHERE id=? AND name=?",other,another);
        }
    }
    @Test void publicationRejectsInvalidTransitionsAndMissingTicket() {
        assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->publication.publish(activity)).getStatusCode().value());
        String another=marker+"-empty";
        jdbc.update("INSERT INTO activity(name,sale_start_time,sale_end_time,event_start_time,status,created_at) VALUES (?, '2030-01-01', '2030-01-03', '2030-01-04', 'DRAFT', UTC_TIMESTAMP(3))",another);
        long empty=jdbc.queryForObject("SELECT id FROM activity WHERE name=?",Long.class,another);
        try { assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->publication.publish(empty)).getStatusCode().value()); }
        finally { jdbc.update("DELETE FROM activity WHERE id=? AND name=?",empty,another); }
    }
    @Test void distinctUsersDifferentTypes() {
        purchases.purchase(request(1,ticket)); purchases.purchase(request(2,otherTicket));
        assertEquals(0,stock(ticket)); assertEquals(4,stock(otherTicket)); assertEquals(2,orders());
    }
    private java.util.List<String> simultaneous(PurchaseRequest a,PurchaseRequest b) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try {
            Callable<String> x=()->call(ready,go,a), y=()->call(ready,go,b);
            Future<String> f=pool.submit(x),g=pool.submit(y);
            assertTrue(ready.await(10,TimeUnit.SECONDS)); go.countDown();
            return java.util.List.of(f.get(20,TimeUnit.SECONDS),g.get(20,TimeUnit.SECONDS));
        } finally { go.countDown(); pool.shutdownNow(); }
    }
    private String call(CountDownLatch ready,CountDownLatch go,PurchaseRequest r) throws Exception {
        ready.countDown(); if(!go.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout");
        try { purchases.purchase(r); return "OK"; }
        catch (PurchaseException e) { return e.code; }
        catch (org.springframework.dao.DuplicateKeyException e) {
            if (e.getMostSpecificCause().getMessage().contains("uq_active_order")) return "DUPLICATE_ORDER";
            throw e;
        }
    }
}
