package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.domain.outbox.BulkSettlementOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.BulkSettlementOutBoxEventRepository;
import com.wageclock.wageclock.domain.outbox.InterBankFailureOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.InterBankFailureOutBoxEventRepository;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodSettlementValidator;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import com.wageclock.wageclock.global.exception.DuplicateException;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Spring 프록시 self-invocation 우회용 분리 클래스
@Component
public class BulkSettlementProcessor {

    private final PayPeriodRepository payPeriodRepository;
    private final BulkSettlementRepository bulkSettlementRepository;
    private final BulkSettlementItemRepository bulkSettlementItemRepository;
    private final BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository;
    private final InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;
    private final PayPeriodSettlementValidator payPeriodSettlementValidator;
    private final EmploymentRepository employmentRepository;

    public BulkSettlementProcessor(PayPeriodRepository payPeriodRepository,
                                   BulkSettlementRepository bulkSettlementRepository,
                                   BulkSettlementItemRepository bulkSettlementItemRepository,
                                   BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository,
                                   InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository,
                                   PayPeriodSettlementValidator payPeriodSettlementValidator, EmploymentRepository employmentRepository) {
        this.payPeriodRepository = payPeriodRepository;
        this.bulkSettlementRepository = bulkSettlementRepository;
        this.bulkSettlementItemRepository = bulkSettlementItemRepository;
        this.bulkSettlementOutBoxEventRepository = bulkSettlementOutBoxEventRepository;
        this.interBankFailureOutBoxEventRepository = interBankFailureOutBoxEventRepository;
        this.payPeriodSettlementValidator = payPeriodSettlementValidator;
        this.employmentRepository = employmentRepository;
    }

    @Transactional
    public BulkSettlement createBulkSettlement(List<Long> employmentIds, Long employerId){
        String portOnePaymentId = "BULK-" + UUID.randomUUID();
        // 출근(clockIn)과 직렬화하기 위한 락. 반환값은 사용하지 않는다.
        employmentRepository.findAllByIdInWithLock(employmentIds);
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
            payPeriodSettlementValidator.validate(payPeriod);
        });
        payPeriods.forEach(PayPeriod::startSettling);

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
    }

    @Transactional
    public void completeItem(Long itemId) {
        BulkSettlementItem item = bulkSettlementItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        item.completed();
        item.getPayPeriod().close();
    }

    @Transactional
    public void markPendingInquiry(Long itemId) {
        BulkSettlementItem item = bulkSettlementItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        item.markPendingInquiry();
    }

    @Transactional
    public void failItem(Long itemId) {
        BulkSettlementItem item = bulkSettlementItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        item.failed();
        // 확정 실패는 돈이 나가지 않은 게 분명하므로 되돌려 재정산 대상이 되게 한다.
        // 미확정(PENDING_INQUIRY·UNKNOWN)은 되돌리지 않는다 — 재이체되면 이중 송금이 된다.
        item.getPayPeriod().reopen();
    }

    @Transactional
    public void unknownItem(Long itemId) {
        BulkSettlementItem bulkSettlementItem = bulkSettlementItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        bulkSettlementItem.unknown();
    }

    @Transactional
    public void assignMessageNo(Long itemId, String messageNo) {
        BulkSettlementItem bulkSettlementItem =  bulkSettlementItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        bulkSettlementItem.assignMessageNo(messageNo);
    }

    @Transactional
    public BulkSettlementContext loadItemContexts(String portOnePaymentId) {
        List<BulkSettlementItem> items = bulkSettlementItemRepository
                .findByBulkSettlement_PortOnePaymentIdAndStatusIn(portOnePaymentId,
                        List.of(BulkSettlementItem.BulkSettlementItemStatus.PENDING));
        if (items.isEmpty()) {
            BulkSettlement bulkSettlement = bulkSettlementRepository.findByPortOnePaymentId(portOnePaymentId)
                    .orElseThrow(() -> new NotFoundException("BulkSettlement not found"));
            return new BulkSettlementContext(bulkSettlement.getId(), List.of());
        }
        List<BulkSettlementItemContext> itemContexts = items.stream()
                .map(item -> new BulkSettlementItemContext(item.getWorkerId(), item.getAmount(),
                        item.getId(), item.getMessageNo()))
                .toList();
        return new BulkSettlementContext(items.getFirst().getBulkSettlement().getId(), itemContexts);
    }

    @Transactional
    public BulkSettlementContext loadPendingInquiryContexts(String portOnePaymentId) {
        List<BulkSettlementItem> items = bulkSettlementItemRepository
                .findByBulkSettlement_PortOnePaymentIdAndStatusIn(portOnePaymentId,
                        List.of(BulkSettlementItem.BulkSettlementItemStatus.PENDING_INQUIRY,
                                BulkSettlementItem.BulkSettlementItemStatus.UNKNOWN));
        if (items.isEmpty()) {
            BulkSettlement bulkSettlement = bulkSettlementRepository.findByPortOnePaymentId(portOnePaymentId)
                    .orElseThrow(() -> new NotFoundException("BulkSettlement not found"));
            return new BulkSettlementContext(bulkSettlement.getId(), List.of());
        }
        List<BulkSettlementItemContext> itemContexts = items.stream()
                .map(item -> new BulkSettlementItemContext(item.getWorkerId(), item.getAmount(),
                        item.getId(), item.getMessageNo()))
                .toList();
        return new BulkSettlementContext(items.getFirst().getBulkSettlement().getId(), itemContexts);
    }

    /**
     * 모든 아이템이 완료됐으면 정산을 COMPLETED로 확정한다.
     *
     * @return 확정 여부. false면 아직 완료되지 않은 아이템이 남아 상태를 바꾸지 않은 것이므로,
     *         호출부가 TRANSFER_FAILED 등으로 착지시켜야 한다. 그대로 두면 TRANSFERRING에 갇힌다.
     */
    @Transactional
    public boolean completeSettlement(String portOnePaymentId) {
        BulkSettlement bulkSettlement = bulkSettlementRepository.findByPortOnePaymentIdWithLock(portOnePaymentId)
                .orElseThrow(() -> new NotFoundException("BulkSettlement not found"));
        boolean allCompleted = bulkSettlement.getItems().stream()
                .allMatch(item -> item.getStatus() == BulkSettlementItem.BulkSettlementItemStatus.COMPLETED);
        if (!allCompleted) return false;
        bulkSettlement.completed();
        return true;
    }

    @Transactional
    public void transferFailSettlement(String portOnePaymentId){
        BulkSettlement bulkSettlement = bulkSettlementRepository.findByPortOnePaymentIdWithLock(portOnePaymentId)
                .orElseThrow(() -> new NotFoundException("BulkSettlement not found"));
        bulkSettlement.transferFailed();
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalAmount(String portOnePaymentId){
        return bulkSettlementRepository.findByPortOnePaymentId(portOnePaymentId)
                .orElseThrow(() -> new NotFoundException(portOnePaymentId + " not found"))
                .getTotalAmount();
    }

    @Transactional
    public void failPayment(String portOnePaymentId){
        BulkSettlement bulkSettlement = bulkSettlementRepository.findByPortOnePaymentId(portOnePaymentId)
                .orElseThrow(() -> new NotFoundException(portOnePaymentId + " not found"));
        // PG가 웹훅을 재전송하면 두 번 들어온다. 두 번째 reopen은 이미 ACTIVE라 예외가 난다.
        if(bulkSettlement.getStatus() == BulkSettlement.BulkSettlementStatus.PAYMENT_FAILED)
            return;
        // 입금 자체가 무산됐으므로 전원 되돌린다. 안 되돌리면 SETTLING에 갇혀
        // 재정산도 출근도 못 하는 상태로 남는다.
        bulkSettlement.getItems().forEach(item -> item.getPayPeriod().reopen());
        bulkSettlement.paymentFailed();
    }

    @Transactional
    public void receiveInterBankFailure(String messageNo){
        BulkSettlementItem item = bulkSettlementItemRepository.findByMessageNo(messageNo)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        item.retrying();
        BulkSettlement settlement = bulkSettlementRepository
                .findByPortOnePaymentIdWithLock(item.getBulkSettlement().getPortOnePaymentId())
                .orElseThrow(() -> new NotFoundException("BulkSettlement not found"));
        if (settlement.getStatus() == BulkSettlement.BulkSettlementStatus.COMPLETED) {
            settlement.retrying();
        }
        InterBankFailureOutBoxEvent event = InterBankFailureOutBoxEvent.builder()
                .messageNo(messageNo)
                .bulkSettlementItemId(item.getId())
                .portOnePaymentId(item.getBulkSettlement().getPortOnePaymentId())
                .bulkSettlementId(item.getBulkSettlement().getId())
                .build();
        interBankFailureOutBoxEventRepository.save(event);
    }
    /**
     * 이체를 선점한다. 선점에 성공한 호출자만 이체를 진행하고, 나머지는 물러난다.
     * <p>
     * 진입점이 셋(웹훅, 스케줄러의 미수신 확인, 스케줄러의 재이체)이라 같은 정산에 동시에 들어올 수 있다.
     * 특히 웹훅은 응답이 늦으면 PG가 재전송하므로 중복 진입이 실제로 발생한다.
     * <p>
     * 상태 확인과 전이 사이에 다른 트랜잭션이 끼어들면 둘 다 통과하므로 행 락이 필요하다.
     * 락은 이 트랜잭션이 끝나면서 풀리고, 이후 긴 이체 작업은 락 없이 진행된다 —
     * 이체는 별도 스레드에서 돌기 때문에 락으로 감싸도 보호되지 않고, 최대 30초씩 걸린다.
     * <p>
     * RETRYING은 제외한다. 타행이체불능 통지를 받은 건은 InterBankFailureOutBox가
     * 아이템 단위로 처리하므로 이 경로로 들어오지 않는다.
     *
     * @return 선점 성공 여부. false면 다른 경로가 이미 처리 중이거나 이미 끝난 정산이다.
     */
    @Transactional
    public boolean claimForTransfer(String portOnePaymentId) {
        BulkSettlement settlement = bulkSettlementRepository
                .findByPortOnePaymentIdWithLock(portOnePaymentId)
                .orElseThrow(() -> new NotFoundException("BulkSettlement not found"));
        if (settlement.getStatus() != BulkSettlement.BulkSettlementStatus.PROCESSING
                && settlement.getStatus() != BulkSettlement.BulkSettlementStatus.TRANSFER_FAILED) {
            return false;
        }
        settlement.transferring();
        return true;
    }


    /**
     * 이체 중 상태로 방치된 정산을 회수한다.
     * <p>
     * TRANSFERRING은 선점된 상태라 어느 스케줄러도 훑지 않는다. 이체 도중 프로세스가 죽으면
     * 아무도 손대지 못하는 상태로 남으므로, 시간이 지난 건을 TRANSFER_FAILED로 되돌려
     * 재이체 스케줄러가 이어받게 한다.
     * <p>
     * messageNo가 발급된 PENDING 아이템은 UNKNOWN으로 돌린다. 번호가 이미 은행에 나갔을 수 있어
     * 재이체하면 이중 송금이 되기 때문이다. UNKNOWN이면 그 번호로 조회해 결과부터 확인한다.
     */
    @Transactional
    public void recoverStaleTransfer(String portOnePaymentId) {
        BulkSettlement settlement = bulkSettlementRepository
                .findByPortOnePaymentIdWithLock(portOnePaymentId)
                .orElseThrow(() -> new NotFoundException("BulkSettlement not found"));
        // 목록을 뽑은 뒤 여기 도달하기까지 사이에 정상 종료됐을 수 있다
        if (settlement.getStatus() != BulkSettlement.BulkSettlementStatus.TRANSFERRING) {
            return;
        }
        settlement.getItems().stream()
                .filter(item -> item.getStatus() == BulkSettlementItem.BulkSettlementItemStatus.PENDING)
                .filter(item -> item.getMessageNo() != null)
                .forEach(BulkSettlementItem::unknown);
        settlement.transferFailed();
    }

    @Transactional
    public void completeRetry(Long itemId) {
        BulkSettlementItem item = bulkSettlementItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        item.completed();
        completeSettlement(item.getBulkSettlement().getPortOnePaymentId());
    }
}