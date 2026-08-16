package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.port.TransferType;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItemRetryContext;
import com.wageclock.wageclock.domain.settlement.BulkSettlementProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InterBankFailureOutBoxEventService {

    private final WageTransferPort wageTransferPort;
    private final BulkSettlementProcessor bulkSettlementProcessor;
    private final InterBankFailureOutBoxProcessor interBankFailureOutBoxProcessor;

    public InterBankFailureOutBoxEventService(WageTransferPort wageTransferPort,
                                              BulkSettlementProcessor bulkSettlementProcessor, InterBankFailureOutBoxProcessor interBankFailureOutBoxProcessor) {
        this.wageTransferPort = wageTransferPort;
        this.bulkSettlementProcessor = bulkSettlementProcessor;
        this.interBankFailureOutBoxProcessor = interBankFailureOutBoxProcessor;
    }

    public void processEvent(InterBankFailureOutBoxEvent event) {
        BulkSettlementItemRetryContext context =
                bulkSettlementProcessor.loadRetryContext(event.getBulkSettlementItemId());
        if (context.needsInquiry()) {
            inquiryTransfer(event, context);
        } else {
            retryTransfer(event, context);
        }
    }

    private String issueMessageNo(Long itemId) {
        String messageNo = wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT);
        bulkSettlementProcessor.assignMessageNo(itemId, messageNo);
        return messageNo;
    }
    private void retryTransfer(InterBankFailureOutBoxEvent event, BulkSettlementItemRetryContext context) {
        Long itemId = context.itemId();
        // 계좌가 없으면 전문번호를 발급하지 않고 재시도 카운트만 태운다.
        // 근로자가 그 사이 계좌를 등록하면 다음 재시도에서 성공하므로 즉시 확정 실패시키지 않는다.
        if (!context.transferAccount().isRegistered()) {
            log.error("계좌 정보 미등록 bulkSettlementItemId={}", itemId);
            interBankFailureOutBoxProcessor.handlePrepareRetryOrFail(event, itemId);
            return;
        }
        String messageNo;
        try{
            messageNo = issueMessageNo(itemId);
        }catch (Exception e){
            log.error("messageNo 발급/저장 실패 bulkSettlementItemId={}", itemId, e);
            interBankFailureOutBoxProcessor.handlePrepareRetryOrFail(event, itemId);
            return;
        }
        try{
            WageTransferResult result = wageTransferPort.transfer(context.transferAccount(),
                    context.amount(), messageNo);
            interBankFailureOutBoxProcessor.applyResult(result, event, itemId);
        }catch (Exception e){
            log.error("이체 처리 실패 bulkSettlementItemId={}", itemId, e);
            interBankFailureOutBoxProcessor.handleRetryOrFail(event, itemId);
        }
    }
    private void inquiryTransfer(InterBankFailureOutBoxEvent event, BulkSettlementItemRetryContext context) {
        Long itemId = context.itemId();
        try {
            WageTransferResult result = wageTransferPort.inquireTransfer(context.messageNo());
            interBankFailureOutBoxProcessor.applyResult(result, event, itemId);
        }catch (Exception e){
            log.error("이체 결과 조회 실패 bulkSettlementItemId={}", itemId, e);
            interBankFailureOutBoxProcessor.handleRetryOrFail(event, itemId);
        }
    }

}
