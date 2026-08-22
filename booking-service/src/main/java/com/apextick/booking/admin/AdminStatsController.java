package com.apextick.booking.admin;

import com.apextick.booking.admin.dto.EventStatsResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/events")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Stats")
public class AdminStatsController {

    private final StatsService stats;

    public AdminStatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/{id}/stats")
    public EventStatsResponse stats(@PathVariable Long id) {
        return stats.eventStats(id);
    }
}
