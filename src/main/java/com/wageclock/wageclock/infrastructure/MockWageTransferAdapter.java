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
    public WageTransferResult transfer(Worker worker, BigDecimal amount, String referenceId) {
        return new WageTransferResult("mock-transfer-" + referenceId, null, referenceId, null);
    }

    @Override
    public WageTransferResult inquireTransfer(String pendingMessageNo) {
        return new WageTransferResult(pendingMessageNo, null, null, null);
    }
}



