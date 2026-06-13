package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItem;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItemRepository;
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

    public InterBankFailureOutBoxEventService(WageTransferPort wageTransferPort,
                                              BulkSettlementItemRepository bulkSettlementItemRepository,
                                              WorkerRepository workerRepository,
                                              InterBankFailureOutBoxEventResultHandler interBankFailureOutBoxEventResultHandler,
                                              InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository) {
        this.wageTransferPort = wageTransferPort;
        this.bulkSettlementItemRepository = bulkSettlementItemRepository;
        this.workerRepository = workerRepository;
        this.interBankFailureOutBoxEventResultHandler = interBankFailureOutBoxEventResultHandler;
        this.interBankFailureOutBoxEventRepository = interBankFailureOutBoxEventRepository;
    }

    public void processEvent(InterBankFailureOutBoxEvent event) {
        BulkSettlementItem failedItem = bulkSettlementItemRepository.findByTransferId(event.getTransferId())
                .orElseThrow(() -> new NotFoundException("BulkSettlementItem Not Found"));
        Worker worker = workerRepository.findById(failedItem.getWorkerId())
                        .orElseThrow(() -> new NotFoundException("Worker Not Found"));
        try {
            WageTransferResult result = wageTransferPort.transfer(worker, failedItem.getAmount(), failedItem.getId());
            if (result.transferId() != null) {
                interBankFailureOutBoxEventResultHandler.saveSuccess(failedItem.getTransferId(), result.transferId(), event);
            } else if (result.pendingMessageNo() != null) {
                WageTransferResult inquireResult = wageTransferPort.inquireTransfer(result.pendingMessageNo(), failedItem.getId());
                if (inquireResult.transferId() != null) {
                    interBankFailureOutBoxEventResultHandler.saveSuccess(failedItem.getTransferId(), inquireResult.transferId(), event);
                }else {
                    event.incrementRetryCount();
                    interBankFailureOutBoxEventRepository.save(event);
                }
            }
        } catch (Exception e) {
            event.incrementRetryCount();
            interBankFailureOutBoxEventRepository.save(event);
        }
    }
}
