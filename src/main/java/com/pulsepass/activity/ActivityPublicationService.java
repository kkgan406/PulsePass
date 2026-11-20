package com.pulsepass.activity;

import java.time.Clock;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ActivityPublicationService {
    private final ActivityMapper activities;
    private final SaleableTicketLookup tickets;
    private final Clock clock;
    public ActivityPublicationService(ActivityMapper activities, SaleableTicketLookup tickets, Clock clock) {
        this.activities=activities; this.tickets=tickets; this.clock=clock;
    }
    @Transactional
    public ActivityResponse publish(long id) {
        Activity a=activities.lockById(id);
        if (a==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Activity not found");
        if (!"DRAFT".equals(a.status) || !a.saleStartTime.isBefore(a.saleEndTime) || a.saleEndTime.isAfter(a.eventStartTime) || LocalDateTime.now(clock).isAfter(a.saleEndTime))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Activity cannot be published");
        if (tickets.count(id)==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Enabled ticket with inventory required");
        a.status="ON_SALE";
        activities.updateById(a);
        return ActivityResponse.from(a);
    }
}

@Mapper
interface SaleableTicketLookup {
    @Select("SELECT COUNT(*) FROM ticket_type t JOIN inventory i ON i.ticket_type_id=t.id WHERE t.activity_id=#{id} AND t.status='ENABLED'")
    int count(@Param("id") long activityId);
}
