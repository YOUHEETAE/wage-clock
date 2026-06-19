package com.wageclock.wageclock.infrastructure;

import com.wageclock.wageclock.domain.EwaTransfer.EwaTransferService;
import com.wageclock.wageclock.domain.port.TransferType;
import com.wageclock.wageclock.domain.settlement.BulkSettlementService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "hectofinancial.mock", havingValue = "true", matchIfMissing = true)
@RequestMapping("/mock/firm-banking")
public class MockFirmBankingSocketListener {

    private final BulkSettlementService bulkSettlementService;
    private final EwaTransferService ewaTransferService;

    public MockFirmBankingSocketListener(BulkSettlementService bulkSettlementService,
                                         EwaTransferService ewaTransferService) {
        this.bulkSettlementService = bulkSettlementService;
        this.ewaTransferService = ewaTransferService;
    }

    @PostMapping("/3000")
    public ResponseEntity<Void> receiveInterBankFailure(
            @RequestBody InterBankFailureNotification notification) {
        String messageNo = notification.originalMessageNo();
        switch (TransferType.fromTransferId(messageNo)) {
            case EWA -> ewaTransferService.receiveInterBankFailure(messageNo);
            case BULK_SETTLEMENT -> bulkSettlementService.receiveInterBankFailure(messageNo);
        }
        //todo : 실제 연동시 통지 수신 후 응답전문 전송 필요
        return ResponseEntity.ok().build();
    }
}
