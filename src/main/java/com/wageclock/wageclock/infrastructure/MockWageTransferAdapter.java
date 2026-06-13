package com.wageclock.wageclock.infrastructure;

import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.worker.Worker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@ConditionalOnProperty(name = "hectofinancial.mock", havingValue = "true", matchIfMissing = true)
public class MockWageTransferAdapter implements WageTransferPort {

    @Override
    public WageTransferResult transfer(Worker worker, BigDecimal amount, Long bulkSettlementItemId) {
        return new WageTransferResult("mock-transfer-" + bulkSettlementItemId, null);
    }

    @Override
    public WageTransferResult inquireTransfer(String pendingMessageNo, Long bulkSettlementItemId) {
        return new WageTransferResult(pendingMessageNo, null);
    }
}



