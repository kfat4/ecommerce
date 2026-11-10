package com.ecommerce.search.api;

import com.ecommerce.search.backfill.BackfillService;
import com.ecommerce.search.backfill.BackfillService.BackfillReport;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search/admin")
public class SearchAdminController {

    private final BackfillService backfillService;

    public SearchAdminController(BackfillService backfillService) {
        this.backfillService = backfillService;
    }

    /**
     * Synchronous for simplicity. For a large catalogue, run it as a background job and return 202 + a status URL.
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    BackfillReport reindex() {
        return backfillService.run();
    }
}
