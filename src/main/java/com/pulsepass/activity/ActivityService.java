package com.pulsepass.activity;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ActivityService {
    private final ActivityMapper mapper;
    public ActivityService(ActivityMapper mapper) { this.mapper = mapper; }

    public ActivityResponse create(CreateActivityRequest request) {
        if (!request.saleStartTime().toInstant().isBefore(request.saleEndTime().toInstant()) ||
            request.saleEndTime().toInstant().isAfter(request.eventStartTime().toInstant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid activity times");
        }
        Activity a = new Activity();
        a.name = request.name(); a.description = request.description();
        a.saleStartTime = utc(request.saleStartTime());
        a.saleEndTime = utc(request.saleEndTime());
        a.eventStartTime = utc(request.eventStartTime());
        // Compare stored millisecond precision too, so truncation cannot break the invariant.
        if (!a.saleStartTime.isBefore(a.saleEndTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sale interval must be at least one millisecond");
        }
        a.status = ActivityStatus.DRAFT.name();
        a.createdAt = LocalDateTime.now(Clock.systemUTC()).truncatedTo(ChronoUnit.MILLIS);
        mapper.insert(a);
        return ActivityResponse.from(a);
    }
    private LocalDateTime utc(java.time.OffsetDateTime t) {
        return LocalDateTime.ofInstant(t.toInstant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }
    public ActivityResponse get(long id) {
        Activity a = mapper.selectById(id);
        if (a == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found");
        return ActivityResponse.from(a);
    }
    public List<ActivityResponse> list(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        return mapper.findPage(size, (long) (page - 1) * size).stream().map(ActivityResponse::from).toList();
    }
}
