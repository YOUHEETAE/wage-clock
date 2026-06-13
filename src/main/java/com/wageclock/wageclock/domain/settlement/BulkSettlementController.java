package com.wageclock.wageclock.domain.settlement;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settlement")
public class BulkSettlementController {

    private final BulkSettlementService bulkSettlementService;

    public BulkSettlementController(BulkSettlementService bulkSettlementService) {
        this.bulkSettlementService = bulkSettlementService;
    }

    @PostMapping("/request")
    public BulkSettlementResponse bulkSettlementRequest(@RequestBody List<Long> employmentIds,
                                                        @AuthenticationPrincipal Long employerId) {
        return bulkSettlementService.bulkSettlementRequest(employmentIds, employerId);
    }
}
