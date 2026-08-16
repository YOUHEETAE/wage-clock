package com.wageclock.wageclock.infrastructure;

import com.wageclock.wageclock.domain.port.TransferAccount;
import com.wageclock.wageclock.domain.port.TransferType;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.global.exception.ExternalApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@ConditionalOnProperty(name = "hectofinancial.mock", havingValue = "false")
public class FirmBankingWageTransferAdapter implements WageTransferPort {

    private static final String PROCESSING_CODE = "PRCS";
    private static final String SUCCESS_CODE = "0000";

    private final FirmBankingService firmBankingService;

    public FirmBankingWageTransferAdapter(FirmBankingService firmBankingService) {
        this.firmBankingService = firmBankingService;
    }

    @Override
    public String prepareTransfer(TransferType type) {
        return firmBankingService.generateMessageNo(type);
    }

    @Override
    public WageTransferResult transfer(TransferAccount transferAccount, BigDecimal amount, String messageNo) {
        HectoFinancialTransferResponse response = firmBankingService.transfer(transferAccount, amount, messageNo);
        if (SUCCESS_CODE.equals(response.responseCode())) {
            return new WageTransferResult(response.messageNo(), null, null);
        }
        // 응답코드 리스트는 은행별로 상이하며 아직 확보하지 못했다.
        // 정상(0000) 외에는 접수 여부를 단정할 수 없으므로 판단을 7000/100 조회에 위임한다.
        // VTIM(응답시간초과)도 이 경로로 처리된다.
        return new WageTransferResult(null, response.messageNo(), null);
    }

    @Override
    public WageTransferResult inquireTransfer(String pendingMessageNo) {
        HectoFinancialInquiryResponse response = firmBankingService.inquireTransferResult(pendingMessageNo);

        // 공통부 응답코드 = 조회 전문 자체의 처리 결과.
        // 조회가 실패하면 원 이체의 결과를 알 수 없으므로 UNKNOWN 경로로 보낸다.
        // 여기서 실패로 확정하면 돈이 나갔을 수도 있는 건의 한도를 되돌리게 된다.
        if (!SUCCESS_CODE.equals(response.responseCode())) {
            throw new ExternalApiException("펌뱅킹 이체결과조회 실패 - responseCode: "
                    + response.responseCode());
        }
        // 이하는 조회가 성공한 경우 — 개별부 처리결과가 원 이체의 결과다.
        if (PROCESSING_CODE.equals(response.processResult())) {
            return new WageTransferResult(null, pendingMessageNo, null);
        }
        if (SUCCESS_CODE.equals(response.processResult())) {
            return new WageTransferResult(pendingMessageNo, null, null);
        }
        // 처리중도 정상도 아니면 이체가 실패로 확정된 것.
        // 응답코드 리스트를 확보하지 못해 사유는 코드값을 그대로 전달한다.
        return new WageTransferResult(null, null, response.processResult());
    }
}