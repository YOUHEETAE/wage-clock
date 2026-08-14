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
    // TODO: 실서비스라면 Standard Webhooks 서명 검증이 필요하다
    //  (webhook-id / webhook-timestamp / webhook-signature 헤더 + PortOne 웹훅 시크릿).
    //  현재는 페이로드를 신뢰하지 않고 PG에 재조회하므로 위조 웹훅으로 상태를 바꿀 수는 없지만,
    //  재조회 자체를 유발하는 요청은 막지 못한다.
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody PortOneWebhookPayload payload) {
        // 페이로드의 type은 신뢰하지 않는다. 어떤 이벤트든 PG에 실제 상태를 다시 물어 판단한다.
        bulkSettlementService.syncPaymentStatus(payload.data().paymentId());
        return ResponseEntity.ok().build();
    }
}
