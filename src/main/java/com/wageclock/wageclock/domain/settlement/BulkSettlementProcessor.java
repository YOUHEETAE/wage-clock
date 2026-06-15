package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.outbox.BulkSettlementOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.BulkSettlementOutBoxEventRepository;
import com.wageclock.wageclock.domain.outbox.InterBankFailureOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.InterBankFailureOutBoxEventRepository;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import com.wageclock.wageclock.global.exception.DuplicateException;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Spring 프록시 self-invocation 우회용 분리 클래스
@Service
public class BulkSettlementProcessor {

    private final PayPeriodRepository payPeriodRepository;
    private final BulkSettlementRepository bulkSettlementRepository;
    private final BulkSettlementItemRepository bulkSettlementItemRepository;
    private final BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository;
    private final InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;

    public BulkSettlementProcessor(PayPeriodRepository payPeriodRepository,
                                   BulkSettlementRepository bulkSettlementRepository,
                                   BulkSettlementItemRepository bulkSettlementItemRepository,
                                   BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository,
                                   InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository) {
        this.payPeriodRepository = payPeriodRepository;
        this.bulkSettlementRepository = bulkSettlementRepository;
        this.bulkSettlementItemRepository = bulkSettlementItemRepository;
        this.bulkSettlementOutBoxEventRepository = bulkSettlementOutBoxEventRepository;
        this.interBankFailureOutBoxEventRepository = interBankFailureOutBoxEventRepository;
    }

    @Transactional
    public BulkSettlement createBulkSettlement(List<Long> employmentIds, Long employerId){
        String portOnePaymentId = "BULK-" + UUID.randomUUID();
        List<PayPeriod> payPeriods = payPeriodRepository
                .findAllByEmploymentIdInAndEmployerIdAndStatusWithLock(employmentIds, employerId);
        if(payPeriods.size() != employmentIds.size()){
            throw  new UnauthorizedException("Unauthorized");
        }
        payPeriods.forEach(payPeriod -> {
            if (bulkSettlementItemRepository.existsByPayPeriod_IdAndBulkSettlement_StatusNotIn(
                    payPeriod.getId(), List.of(BulkSettlement.BulkSettlementStatus.TRANSFER_FAILED,
                            BulkSettlement.BulkSettlementStatus.COMPLETED,
                            BulkSettlement.BulkSettlementStatus.PAYMENT_FAILED))) {
                throw new DuplicateException("이미 진행 중인 정산이 있습니다.");
            }
        });

        BigDecimal totalAmount = payPeriods.stream()
                .map(PayPeriod::getActualPayAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BulkSettlement bulkSettlement = BulkSettlement.builder()
                .portOnePaymentId(portOnePaymentId)
                .employerId(employerId)
                .totalAmount(totalAmount).build();
        payPeriods.forEach(payPeriod -> bulkSettlement.addItem(BulkSettlementItem.builder()
                .bulkSettlement(bulkSettlement)
                .payPeriod(payPeriod)
                .amount(payPeriod.getActualPayAmount())
                .build()));
        bulkSettlementRepository.save(bulkSettlement);

        BulkSettlementOutBoxEvent bulkSettlementOutBoxEvent = BulkSettlementOutBoxEvent.builder()
                .bulkSettlementId(bulkSettlement.getId())
                .portOnePaymentId(portOnePaymentId)
                .totalAmount(totalAmount)
                .employerName(payPeriods.getFirst().getEmployerName()).build();
        bulkSettlementOutBoxEventRepository.save(bulkSettlementOutBoxEvent);
        return bulkSettlement;
    }

    @Transactional
    public void updateAccountInfo(VirtualAccountResult account, BulkSettlement bulkSettlement){
        bulkSettlement.updateAccountInfo(account.bank(), account.accountNumber(), account.expiredAt());
        bulkSettlement.processing();
        bulkSettlementRepository.save(bulkSettlement);
        BulkSettlementOutBoxEvent bulkSettlementOutBoxEvent = bulkSettlementOutBoxEventRepository.
                findByPortOnePaymentId(bulkSettlement.getPortOnePaymentId()).
                orElseThrow(() -> new NotFoundException("BulkSettlementOutBoxEvent not found."));
        bulkSettlementOutBoxEvent.processed();
        bulkSettlementOutBoxEventRepository.save(bulkSettlementOutBoxEvent);
    }
    @Transactional
    public void assignTransferId(Long itemId, String transferId) {
        BulkSettlementItem item = bulkSettlementItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        item.assignTransferId(transferId);
        item.getPayPeriod().close();
        bulkSettlementItemRepository.save(item);
    }

    @Transactional
    public void markPendingInquiry(Long itemId, String pendingMessageNo) {
        BulkSettlementItem item = bulkSettlementItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        item.markPendingInquiry(pendingMessageNo);
        bulkSettlementItemRepository.save(item);
    }

    @Transactional
    public void markFailed(Long itemId) {
        BulkSettlementItem item = bulkSettlementItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        item.markFailed();
        bulkSettlementItemRepository.save(item);
    }

    @Transactional
    public BulkSettlementContext loadItemContexts(String portOnePaymentId) {
        List<BulkSettlementItem> items = bulkSettlementItemRepository
                .findByBulkSettlement_PortOnePaymentIdAndStatusIn(portOnePaymentId,
                        List.of(BulkSettlementItem.BulkSettlementItemStatus.PENDING,
                                BulkSettlementItem.BulkSettlementItemStatus.FAILED));
        if (items.isEmpty()) {
            BulkSettlement bulkSettlement = bulkSettlementRepository.findByPortOnePaymentId(portOnePaymentId)
                    .orElseThrow(() -> new NotFoundException("BulkSettlement not found"));
            return new BulkSettlementContext(bulkSettlement.getId(), List.of());
        }
        List<BulkSettlementItemContext> itemContexts = items.stream()
                .map(item -> new BulkSettlementItemContext(item.getWorkerId(), item.getAmount(),
                        item.getId(), item.getTransferId(), null))
                .toList();
        return new BulkSettlementContext(items.getFirst().getBulkSettlement().getId(), itemContexts);
    }

    @Transactional
    public BulkSettlementContext loadPendingInquiryContexts(String portOnePaymentId) {
        List<BulkSettlementItem> items = bulkSettlementItemRepository
                .findByBulkSettlement_PortOnePaymentIdAndStatusIn(portOnePaymentId,
                        List.of(BulkSettlementItem.BulkSettlementItemStatus.PENDING_INQUIRY));
        if (items.isEmpty()) {
            BulkSettlement bulkSettlement = bulkSettlementRepository.findByPortOnePaymentId(portOnePaymentId)
                    .orElseThrow(() -> new NotFoundException("BulkSettlement not found"));
            return new BulkSettlementContext(bulkSettlement.getId(), List.of());
        }
        List<BulkSettlementItemContext> itemContexts = items.stream()
                .map(item -> new BulkSettlementItemContext(item.getWorkerId(), item.getAmount(),
                        item.getId(), item.getTransferId(), item.getPendingMessageNo()))
                .toList();
        return new BulkSettlementContext(items.getFirst().getBulkSettlement().getId(), itemContexts);
    }

    @Transactional
    public void completeSettlement(String portOnePaymentId) {
        BulkSettlement bulkSettlement = bulkSettlementRepository.findByPortOnePaymentId(portOnePaymentId)
                .orElseThrow(() -> new NotFoundException("BulkSettlement not found"));
        boolean allCompleted = bulkSettlement.getItems().stream()
                .allMatch(item -> item.getStatus() == BulkSettlementItem.BulkSettlementItemStatus.COMPLETED);
        if (!allCompleted) return;
        bulkSettlement.completed();
        bulkSettlementRepository.save(bulkSettlement);
    }
    @Transactional
    public void failedSettlement(String portOnePaymentId){
        BulkSettlement bulkSettlement = bulkSettlementRepository.findByPortOnePaymentId(portOnePaymentId)
                .orElseThrow(() -> new NotFoundException("BulkSettlement not found"));
        bulkSettlement.transferFailed();
        bulkSettlementRepository.save(bulkSettlement);
    }
    @Transactional
    public void failedPayment(String portOnePaymentId){
        BulkSettlement bulkSettlement = bulkSettlementRepository.findByPortOnePaymentId(portOnePaymentId)
                .orElseThrow(() -> new NotFoundException(portOnePaymentId + " not found"));
        bulkSettlement.paymentFailed();
        bulkSettlementRepository.save(bulkSettlement);
    }
    @Transactional
    public void receiveInterBankFailure(String transferId){
        BulkSettlementItem item = bulkSettlementItemRepository.findByTransferId(transferId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        item.markFailed();
        bulkSettlementItemRepository.save(item);
        InterBankFailureOutBoxEvent event = InterBankFailureOutBoxEvent.builder()
                .transferId(transferId)
                .portOnePaymentId(item.getBulkSettlement().getPortOnePaymentId())
                .bulkSettlementId(item.getBulkSettlement().getId())
                .build();
        interBankFailureOutBoxEventRepository.save(event);
    }

}
