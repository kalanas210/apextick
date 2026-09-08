package com.apextick.booking.catalog.admin;

import com.apextick.booking.catalog.Sport;
import com.apextick.booking.catalog.Team;
import com.apextick.booking.catalog.TeamRepository;
import com.apextick.booking.catalog.dto.TeamResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lookups the admin event form needs. Events are created with homeTeamId/awayTeamId,
 * and nothing else in the API exposes teams — without this the form is reduced to
 * asking an operator to type raw database ids.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Catalog")
public class AdminCatalogController {

    private final TeamRepository teams;

    public AdminCatalogController(TeamRepository teams) {
        this.teams = teams;
    }

    @GetMapping("/teams")
    @Operation(summary = "Teams available to pick as an event's home or away side")
    public List<TeamResponse> teams(@RequestParam(required = false) String sport) {
        List<Team> found = sport == null || sport.isBlank()
                ? teams.findAllByOrderByNameAsc()
                : teams.findBySportOrderByNameAsc(Sport.fromCode(sport));
        return found.stream().map(TeamResponse::from).toList();
    }
}
