package com.wageclock.wageclock.infrastructure;


import com.wageclock.wageclock.domain.settlement.BulkSettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PortOneWebhookController {

    private final BulkSettlementService bulkSettlementService;

    public PortOneWebhookController(BulkSettlementService bulkSettlementService) {
        this.bulkSettlementService = bulkSettlementService;
    }
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody PortOneWebhookPayload payload) {
        String portOnePaymentId = payload.data().paymentId();
        if ("Transaction.Paid".equals(payload.type())) {
            bulkSettlementService.initiateBulkSettlement(portOnePaymentId);
        } else if ("Transaction.Cancelled".equals(payload.type()) || "Transaction.Failed".equals(payload.type())) {
            bulkSettlementService.failedPayment(portOnePaymentId);
        }
        return ResponseEntity.ok().build();
    }
}
