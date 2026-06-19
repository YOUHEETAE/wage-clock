package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.port.TransferType;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItem;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItemRepository;
import com.wageclock.wageclock.domain.settlement.BulkSettlementProcessor;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.springframework.stereotype.Service;

@Service
public class InterBankFailureOutBoxEventService {

    private final WageTransferPort wageTransferPort;
    private final BulkSettlementItemRepository bulkSettlementItemRepository;
    private final WorkerRepository workerRepository;
    private final InterBankFailureOutBoxEventResultHandler interBankFailureOutBoxEventResultHandler;
    private final InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;
    private final BulkSettlementProcessor bulkSettlementProcessor;

    public InterBankFailureOutBoxEventService(WageTransferPort wageTransferPort,
                                              BulkSettlementItemRepository bulkSettlementItemRepository,
                                              WorkerRepository workerRepository,
                                              InterBankFailureOutBoxEventResultHandler interBankFailureOutBoxEventResultHandler,
                                              InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository, BulkSettlementProcessor bulkSettlementProcessor) {
        this.wageTransferPort = wageTransferPort;
        this.bulkSettlementItemRepository = bulkSettlementItemRepository;
        this.workerRepository = workerRepository;
        this.interBankFailureOutBoxEventResultHandler = interBankFailureOutBoxEventResultHandler;
        this.interBankFailureOutBoxEventRepository = interBankFailureOutBoxEventRepository;
        this.bulkSettlementProcessor = bulkSettlementProcessor;
    }

    public void processEvent(InterBankFailureOutBoxEvent event) {
        BulkSettlementItem bulkSettlementItem = bulkSettlementItemRepository.findByIdWithEmployment(event.getBulkSettlementItemId())
                .orElseThrow(() -> new NotFoundException("BulkSettlementItem Not Found"));
        Worker worker = workerRepository.findById(bulkSettlementItem.getWorkerId())
                        .orElseThrow(() -> new NotFoundException("Worker Not Found"));
        try {
            WageTransferResult result;
            BulkSettlementItem.BulkSettlementItemStatus status = bulkSettlementItem.getStatus();
            if (status == BulkSettlementItem.BulkSettlementItemStatus.PENDING_INQUIRY
                    || status == BulkSettlementItem.BulkSettlementItemStatus.UNKNOWN) {
                result = wageTransferPort.inquireTransfer(bulkSettlementItem.getMessageNo());
            } else {
                String messageNo = wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT);
                bulkSettlementProcessor.assignMessageNo(bulkSettlementItem.getId(), messageNo);
                result = wageTransferPort.transfer(worker, bulkSettlementItem.getAmount(), messageNo);
            }
            if (result.transferId() != null) {
                    interBankFailureOutBoxEventResultHandler.saveSuccess(bulkSettlementItem, event);
                }else  if (result.pendingMessageNo() != null) {
                    bulkSettlementProcessor.markPendingInquiry(bulkSettlementItem.getId());
                }else if(result.failureReason() != null) {
                    bulkSettlementProcessor.failItem(bulkSettlementItem.getId());
                    event.failed();
                    interBankFailureOutBoxEventRepository.save(event);
                    //todo : 확정 실패시 알림 발송 필요
                }
                else {
                    event.incrementRetryCount();
                    if (event.getStatus() == InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.FAILED) {
                        bulkSettlementProcessor.failItem(bulkSettlementItem.getId());
                        //todo : 확정 실패시 알림 발송 필요
                    } else {
                        bulkSettlementProcessor.unknownItem(bulkSettlementItem.getId());
                    }
                    interBankFailureOutBoxEventRepository.save(event);
                }
        } catch (Exception e) {
            event.incrementRetryCount();
            if (event.getStatus() == InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.FAILED) {
                bulkSettlementProcessor.failItem(bulkSettlementItem.getId());
                //todo : 확정 실패시 알림 발송 필요
            } else {
                bulkSettlementProcessor.unknownItem(bulkSettlementItem.getId());
            }
            interBankFailureOutBoxEventRepository.save(event);
        }
    }
}
