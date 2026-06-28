package com.wageclock.wageclock.infrastructure;

import com.wageclock.wageclock.domain.port.TransferType;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.global.exception.ExternalApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@ConditionalOnProperty(name = "hectofinancial.mock", havingValue = "false")
public class FirmBankingWageTransferAdapter implements WageTransferPort {

    private static final String PROCESSING_CODE = "PRCS";

    private final FirmBankingService firmBankingService;

    public FirmBankingWageTransferAdapter(FirmBankingService firmBankingService) {
        this.firmBankingService = firmBankingService;
    }

    @Override
    public String prepareTransfer(TransferType type) {
        return firmBankingService.generateMessageNo(type);
    }

    @Override
    public WageTransferResult transfer(Worker worker, BigDecimal amount, String messageNo) {
        HectoFinancialTransferResponse response = firmBankingService.transfer(worker, amount, messageNo);
        if ("0000".equals(response.responseCode())) {
            return new WageTransferResult(response.messageNo(), null, null);
        }
        // 비정상 응답 → 재조회 필요 (VTIM 포함)
        return new WageTransferResult(null, response.messageNo(), null);
    }

    @Override
    public WageTransferResult inquireTransfer(String pendingMessageNo) {
        HectoFinancialInquiryResponse response = firmBankingService.inquireTransferResult(pendingMessageNo);
        if (PROCESSING_CODE.equals(response.processResult())) {
            return new WageTransferResult(null, pendingMessageNo, null);
        }
        if ("0000".equals(response.responseCode()) && "0000".equals(response.processResult())) {
            return new WageTransferResult(pendingMessageNo, null, null);
        }
        throw new ExternalApiException("펌뱅킹 이체 실패 - responseCode: "
                + response.responseCode() + ", processResult: " + response.processResult());
    }
}