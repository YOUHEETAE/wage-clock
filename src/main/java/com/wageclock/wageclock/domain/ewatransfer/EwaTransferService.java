package com.wageclock.wageclock.domain.ewatransfer;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;
import com.wageclock.wageclock.domain.port.TransferType;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class EwaTransferService {

    private final WageTransferPort wageTransferPort;
    private final EwaTransferProcessor ewaTransferProcessor;

    public EwaTransferService(WageTransferPort wageTransferPort, EwaTransferProcessor ewaTransferProcessor) {

        this.wageTransferPort = wageTransferPort;
        this.ewaTransferProcessor = ewaTransferProcessor;
    }

    public EwaRequest.EwaRequestStatus processTransfer(Long ewaRequestId) {
        EwaTransferContext ewaTransferContext = ewaTransferProcessor.createEwaTransfer(ewaRequestId);
        Long ewaTransferId = ewaTransferContext.ewaTransferId();
        // 보낼 수 없는 건에 전문번호를 발급할 이유가 없으므로 발급 전에 막는다.
        // 돈이 나가지 않은 것이 확실하므로 확정 실패로 떨어뜨려 한도를 되돌린다.
        if (!ewaTransferContext.transferAccount().isRegistered()) {
            log.error("계좌 정보 미등록 ewaTransferId={}", ewaTransferId);
            ewaTransferProcessor.failTransfer(ewaTransferId);
            return EwaRequest.EwaRequestStatus.FAILED;
        }
        String messageNo;
        try {
            messageNo = issueTransferMessageNo(ewaTransferId);
        }catch (Exception e){
            log.error("messageNo 발급/저장 실패 ewaTransferId={}", ewaTransferId, e);
            ewaTransferProcessor.failTransfer(ewaTransferId);
            return EwaRequest.EwaRequestStatus.FAILED;
        }
        try{
            WageTransferResult result = wageTransferPort.transfer(ewaTransferContext.transferAccount(),
                    ewaTransferContext.amount(), messageNo);
            return applyTransferStatus(result, ewaTransferId);
        }catch (Exception e){
            log.error("이체 처리 실패 EwaTransferId={}", ewaTransferId, e);
            ewaTransferProcessor.unknownTransfer(ewaTransferId);
            return EwaRequest.EwaRequestStatus.UNKNOWN;
        }
    }

    public void receiveInterBankFailure(String transferId) {
        ewaTransferProcessor.receiveInterBankFailure(transferId);
    }

    public void inquiryTransfer(EwaTransferInquiryContext context) {
        Long ewaTransferId = context.ewaTransferId();
       try {
           WageTransferResult result = wageTransferPort.inquireTransfer(context.messageNo());
           applyTransferStatus(result, ewaTransferId);
       }catch (Exception e){
           log.error("이체 결과 조회 실패 EwaTransferId={}", ewaTransferId, e);
           ewaTransferProcessor.unknownTransfer(ewaTransferId);
       }
    }

    /**
     * 이체할 때마다 새 전문번호를 발급한다. 기존 번호를 재사용하지 않는 이유는,
     * 이미 은행에 전송된 번호라면 재이체가 아니라 조회로 결과를 확인해야 하기 때문이다.
     * <p>
     * 그래서 호출부는 발급 실패와 이체 실패를 분리해 처리한다.
     * 발급 실패는 은행에 요청이 나가지 않은 상태이므로 확정 실패로 떨어뜨리고,
     * 이체 실패는 번호가 이미 나갔을 수 있으므로 UNKNOWN으로 두고 그 번호로 조회한다.
     * 두 경로를 한 try-catch로 묶으면 발급 실패한 건이 없는 번호로 조회를 시도하게 된다.
     */
    private String issueTransferMessageNo(Long ewaTransferId) {
        String messageNo = wageTransferPort.prepareTransfer(TransferType.EWA);
        ewaTransferProcessor.assignMessageNo(ewaTransferId ,messageNo);
        return messageNo;
    }

    private EwaRequest.EwaRequestStatus applyTransferStatus(WageTransferResult result, Long ewaTransferId) {
        return switch (result.classify()) {
            case SUCCESS -> {
                ewaTransferProcessor.completeTransfer(ewaTransferId);
                yield EwaRequest.EwaRequestStatus.APPROVED;
            }
            case PENDING_INQUIRY -> {
                ewaTransferProcessor.markPendingInquiry(ewaTransferId);
                yield EwaRequest.EwaRequestStatus.PENDING;
            }
            case FAILURE -> {
                ewaTransferProcessor.failTransfer(ewaTransferId);
                yield EwaRequest.EwaRequestStatus.FAILED;
                //todo : 확정 실패시 알림 발송 필요
            }
            case UNKNOWN -> {
                ewaTransferProcessor.unknownTransfer(ewaTransferId);
                yield EwaRequest.EwaRequestStatus.UNKNOWN;
            }
        };
    }
}
