package com.wageclock.wageclock.infrastructure;


import com.wageclock.wageclock.domain.ewa.EwaSettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PortOneWebhookController {

    private final EwaSettlementService ewaSettlementService;

    public PortOneWebhookController(EwaSettlementService ewaSettlementService) {
        this.ewaSettlementService = ewaSettlementService;
    }
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody PortOneWebhookPayload payload){
        if("Transaction.Paid".equals(payload.type())){
            ewaSettlementService.approveEwa(payload.data().paymentId());
        }
       return ResponseEntity.ok().build();
    }
}
