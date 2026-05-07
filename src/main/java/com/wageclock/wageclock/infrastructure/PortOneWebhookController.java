package com.wageclock.wageclock.infrastructure;


import com.wageclock.wageclock.domain.ewa.PortOneWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PortOneWebhookController {

    private final PortOneWebhookService portOneWebhookService;

    public PortOneWebhookController(PortOneWebhookService portOneWebhookService) {
        this.portOneWebhookService = portOneWebhookService;
    }
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody PortOneWebhookPayload payload){
        if("Transaction.Paid".equals(payload.type())){
            portOneWebhookService.approveEwa(payload.data().paymentId());
        }
       return ResponseEntity.ok().build();
    }
}
