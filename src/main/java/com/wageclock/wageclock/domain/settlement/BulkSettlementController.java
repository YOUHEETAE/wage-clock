package com.wageclock.wageclock.domain.settlement;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settlements")
public class BulkSettlementController {

    private final BulkSettlementService bulkSettlementService;

    public BulkSettlementController(BulkSettlementService bulkSettlementService) {
        this.bulkSettlementService = bulkSettlementService;
    }

    @Operation(summary = "일괄 정산 요청 (직원 N명 선택, 가상계좌 발급)")
    @PostMapping("/request")
    public BulkSettlementResponse request(@RequestBody List<Long> employmentIds,
                                                        @AuthenticationPrincipal Long employerId) {
        return bulkSettlementService.requestBulkSettlement(employmentIds, employerId);
    }
}
