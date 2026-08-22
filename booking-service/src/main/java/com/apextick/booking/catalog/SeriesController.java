package com.apextick.booking.catalog;

import com.apextick.booking.catalog.dto.SeriesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/series")
@Tag(name = "Series")
public class SeriesController {

    private final CatalogQueryService catalog;

    public SeriesController(CatalogQueryService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(summary = "All series")
    public List<SeriesResponse> all() {
        return catalog.series();
    }

    @GetMapping("/{slug}")
    @Operation(summary = "A single series by slug")
    public SeriesResponse one(@PathVariable String slug) {
        return catalog.seriesBySlug(slug);
    }
}
