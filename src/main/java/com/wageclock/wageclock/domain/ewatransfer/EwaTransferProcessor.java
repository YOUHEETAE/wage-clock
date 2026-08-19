package com.wageclock.wageclock.domain.ewatransfer;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;
import com.wageclock.wageclock.domain.ewarequest.EwaRequestRepository;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxRepository;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Component
public class EwaTransferProcessor {

    private final EwaTransferRepository ewaTransferRepository;
    private final EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;
    private final PayPeriodRepository payPeriodRepository;
    private final EwaRequestRepository ewaRequestRepository;

    public EwaTransferProcessor(EwaTransferRepository ewaTransferRepository, EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository, PayPeriodRepository payPeriodRepository, EwaRequestRepository ewaRequestRepository){

        this.ewaTransferRepository = ewaTransferRepository;
        this.ewaTransferFailureOutBoxRepository = ewaTransferFailureOutBoxRepository;
        this.payPeriodRepository = payPeriodRepository;
        this.ewaRequestRepository = ewaRequestRepository;
    }

    @Transactional
    /**
     * 이체 건을 만들고, 이후 이체에 필요한 값을 컨텍스트로 확정해 돌려준다.
     * <p>
     * 엔티티가 아니라 ID를 받는다. 앞 트랜잭션에서 조회한 EwaRequest를 넘겨받으면 준영속
     * 상태라, 여기서 파생된 계좌를 트랜잭션 밖에서 읽을 때 지연 로딩으로 터진다.
     * 자기 트랜잭션 안에서 다시 조회해야 계좌를 값으로 확정할 수 있다.
     */
    public EwaTransferContext createEwaTransfer(Long ewaRequestId){
        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaRequestId)
                .orElseThrow(() -> new NotFoundException("EwaRequest Not Found"));
        EwaTransfer ewaTransfer = EwaTransfer.builder()
                .ewaRequest(ewaRequest)
                .amount(ewaRequest.getRequestedAmount())
                .build();
        ewaTransferRepository.save(ewaTransfer);
        return new EwaTransferContext(
                ewaTransfer.getId(),
                ewaTransfer.getAmount(),
                ewaRequest.getWorker().toTransferAccount());
    }

    /** 조회 대상만 값으로 뽑는다. 스케줄러가 엔티티를 들고 다니면 트랜잭션 밖에서 지연 로딩이 일어난다. */
    @Transactional(readOnly = true)
    public List<EwaTransferInquiryContext> loadInquiryContexts(){
        return ewaTransferRepository.findByStatusIn(
                        List.of(EwaTransfer.EwaTransferStatus.PENDING_INQUIRY,
                                EwaTransfer.EwaTransferStatus.UNKNOWN)).stream()
                .map(transfer -> new EwaTransferInquiryContext(transfer.getId(), transfer.getMessageNo()))
                .toList();
    }

    /** 아웃박스 재처리에 필요한 값을 트랜잭션 안에서 확정한다. */
    @Transactional(readOnly = true)
    public EwaTransferRetryContext loadRetryContext(Long ewaTransferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findByIdWithWorker(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("EwaTransfer Not Found"));
        return new EwaTransferRetryContext(ewaTransfer.getId(), ewaTransfer.getStatus(),
                ewaTransfer.getAmount(), ewaTransfer.getMessageNo(),
                ewaTransfer.getWorker().toTransferAccount());
    }

    @Transactional
    public void assignMessageNo(Long ewaTransferId ,String messageNo){
        EwaTransfer ewaTransfer = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("EwaTransfer not found"));
        ewaTransfer.assignMessageNo(messageNo);
    }

    @Transactional
    public void completeTransfer(Long ewaTransferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("Transfer not found"));
        ewaTransfer.completed();
        ewaTransfer.getEwaRequest().approved();
    }

    @Transactional
    public void markPendingInquiry(Long ewaTransferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("Transfer not found"));
        ewaTransfer.markPendingInquiry();
    }

    @Transactional
    public void failTransfer(Long ewaTransferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("Transfer not found"));
        ewaTransfer.failed();
        ewaTransfer.getEwaRequest().failed();
        PayPeriod payPeriod = payPeriodRepository.findByIdWithLock(ewaTransfer.getEwaRequest().getPayPeriodId())
                .orElseThrow(() -> new NotFoundException("PayPeriod not found"));
        // 요청 시점에 잡아둔 한도를 되돌린다 (이체가 나가지 않았음이 확정됨)
        payPeriod.subtractEwaAmount(ewaTransfer.getAmount());
    }

    @Transactional
    public void unknownTransfer(Long ewaTransferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("Transfer not found"));
        ewaTransfer.unknown();
        ewaTransfer.getEwaRequest().unknown();
    }
    @Transactional
    public void receiveInterBankFailure(String transferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findByMessageNo(transferId)
                .orElseThrow(() -> new NotFoundException("ewaTransfer not found"));
        // RETRYING은 아직 미확정이므로 한도를 되돌리지 않는다.
        // 여기서 되돌리면 재시도 성공 시 다시 더해야 하고, 그러면 최초 성공 경로와
        // 재시도 성공 경로가 completeTransfer를 공유하면서 한쪽이 반드시 틀어진다.
        ewaTransfer.retrying();
        EwaTransferFailureOutBoxEvent event = EwaTransferFailureOutBoxEvent.builder()
                .ewaTransferId(ewaTransfer.getId())
                .messageNo(transferId)
                .amount(ewaTransfer.getAmount()).build();
        ewaTransferFailureOutBoxRepository.save(event);
    }

    @Transactional
    public void completeRetry(Long ewaTransferId){
        EwaTransfer managed = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("EwaTransfer Not Found"));
        managed.completed();
        managed.getEwaRequest().approved();
    }

    @Transactional
    public void failRetry(Long ewaTransferId){
        EwaTransfer managed = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("EwaTransfer Not Found"));
        managed.failed();
        managed.getEwaRequest().failed();
        PayPeriod payPeriod = payPeriodRepository.findByIdWithLock(managed.getEwaRequest().getPayPeriodId())
                .orElseThrow(() -> new NotFoundException("PayPeriod not found"));
        // 요청 시점에 잡아둔 한도를 되돌린다.
        // 아웃박스가 FAILED 상태를 조기 종료 처리하므로 중복 차감되지 않는다.
        payPeriod.subtractEwaAmount(managed.getAmount());
    }

    @Transactional
    public void unKnownRetry(Long ewaTransferId){
        EwaTransfer managed = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("EwaTransfer Not Found"));
        managed.unknown();
        managed.getEwaRequest().unknown();
    }
}
