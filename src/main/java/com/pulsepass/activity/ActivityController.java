package com.pulsepass.activity;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {
    private final ActivityService service;
    public ActivityController(ActivityService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ActivityResponse> create(@Valid @RequestBody CreateActivityRequest request) {
        return ApiResponse.success(service.create(request));
    }
    @GetMapping("/{activityId}")
    public ApiResponse<ActivityResponse> get(@PathVariable long activityId) { return ApiResponse.success(service.get(activityId)); }
    @GetMapping
    public ApiResponse<List<ActivityResponse>> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) { return ApiResponse.success(service.list(page, size)); }
}
