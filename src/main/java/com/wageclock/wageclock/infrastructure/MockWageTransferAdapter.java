package com.wageclock.wageclock.infrastructure;

import com.wageclock.wageclock.domain.port.TransferType;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.worker.Worker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@ConditionalOnProperty(name = "hectofinancial.mock", havingValue = "true", matchIfMissing = true)
public class MockWageTransferAdapter implements WageTransferPort {

    private final AtomicInteger sequence = new AtomicInteger();

    @Override
    public String prepareTransfer(TransferType type) {
        return type.getPrefix() + String.format("%05d", sequence.incrementAndGet());
    }

    @Override
    public WageTransferResult transfer(Worker worker, BigDecimal amount, String messageNo) {
        return new WageTransferResult("mock-transfer-" + messageNo, null, null);
    }

    @Override
    public WageTransferResult inquireTransfer(String pendingMessageNo) {
        return new WageTransferResult(pendingMessageNo, null, null);
    }
}



