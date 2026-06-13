package com.wageclock.wageclock.infrastructure;


import com.wageclock.wageclock.domain.ewa.EwaSettlementService;
import com.wageclock.wageclock.domain.settlement.BulkSettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PortOneWebhookController {

    private final EwaSettlementService ewaSettlementService;
    private final BulkSettlementService bulkSettlementService;

    public PortOneWebhookController(EwaSettlementService ewaSettlementService,
                                    BulkSettlementService bulkSettlementService) {
        this.ewaSettlementService = ewaSettlementService;
        this.bulkSettlementService = bulkSettlementService;
    }
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody PortOneWebhookPayload payload){
        String portOnePaymentId = payload.data().paymentId();
        if ("Transaction.Paid".equals(payload.type())) {
            if (portOnePaymentId.startsWith("EWA-")) {
                ewaSettlementService.approveEwa(portOnePaymentId);
            } else if (portOnePaymentId.startsWith("BULK-")) {
                bulkSettlementService.initiateBulkSettlement(portOnePaymentId);
            }
        } else if("Transaction.Cancelled".equals(payload.type()) || "Transaction.Failed".equals(payload.type())){
            if(portOnePaymentId.startsWith("EWA-")) {
                ewaSettlementService.failEwa(payload.data().paymentId());
            } else if(portOnePaymentId.startsWith("BULK-")) {
                bulkSettlementService.failedPayment(portOnePaymentId);
            }
        }
       return ResponseEntity.ok().build();
    }
}
