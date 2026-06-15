package com.wageclock.wageclock.domain.EwaTransfer;

import com.wageclock.wageclock.domain.ewa.EwaRequest;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "ewa_transfers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EwaTransfer extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ewa_request_id")
    private EwaRequest ewaRequest;

    @Column(nullable = false)
    private BigDecimal amount;

    private String transferId;

    private String pendingMessageNo;

    @Enumerated(EnumType.STRING)
    private EwaTransferStatus status;

    public enum EwaTransferStatus {
        PENDING,
        PENDING_INQUIRY,
        COMPLETED,
        FAILED,
        UNKNOWN,
        RETRYING
    }

    @Builder
    public EwaTransfer(EwaRequest ewaRequest, BigDecimal amount) {
        this.ewaRequest = ewaRequest;
        this.amount = amount;
        this.status = EwaTransferStatus.PENDING;
    }

    public void assignTransferId(String transferId) {
        this.transferId = transferId;
        this.status = EwaTransferStatus.COMPLETED;
    }
    public void markPendingInquiry(String pendingMessageNo) {
        this.pendingMessageNo = pendingMessageNo;
        this.status = EwaTransferStatus.PENDING_INQUIRY;
    }
    public void markFailed(){
        this.status = EwaTransferStatus.FAILED;
    }
    public Worker getWorker(){
        return this.ewaRequest.getWorker();
    }
    public void markUnknown(){
        this.status = EwaTransferStatus.UNKNOWN;
    }
    public void markRetrying(){
        this.status = EwaTransferStatus.RETRYING;
    }
    public Long getWorkerId(){
        return this.ewaRequest.getWorker().getId();
    }
}
