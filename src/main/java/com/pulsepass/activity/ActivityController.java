package com.pulsepass.activity;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {
    private final ActivityService service;
    private final ActivityPublicationService publication;
    public ActivityController(ActivityService service, ActivityPublicationService publication) { this.service = service; this.publication = publication; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ActivityResponse> create(@Valid @RequestBody CreateActivityRequest request) {
        return ApiResponse.success(service.create(request));
    }
    @GetMapping("/{activityId}")
    public ApiResponse<ActivityResponse> get(@PathVariable long activityId) { return ApiResponse.success(service.get(activityId)); }
    @GetMapping
    public ApiResponse<List<ActivityResponse>> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) { return ApiResponse.success(service.list(page, size)); }
    public record StatusRequest(String status) { }
    @PatchMapping("/{activityId}/status")
    public ApiResponse<ActivityResponse> publish(@PathVariable long activityId, @RequestBody StatusRequest request) {
        if (!"ON_SALE".equals(request.status())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Only ON_SALE is supported");
        return ApiResponse.success(publication.publish(activityId));
    }
}
