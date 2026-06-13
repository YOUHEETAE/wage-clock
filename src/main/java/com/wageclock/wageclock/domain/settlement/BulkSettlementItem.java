package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "bulk_settlement_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BulkSettlementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bulk_settlement_id")
    private BulkSettlement bulkSettlement;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pay_period_id")
    private PayPeriod payPeriod;

    @Column(nullable = false)
    private BigDecimal amount;

    private String transferId;

    private String pendingMessageNo;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BulkSettlementItemStatus status;

    public enum BulkSettlementItemStatus {
        PENDING,
        PENDING_INQUIRY,
        COMPLETED,
        FAILED
    }

    @Builder
    public BulkSettlementItem(BulkSettlement bulkSettlement, PayPeriod payPeriod, BigDecimal amount) {
        this.bulkSettlement = bulkSettlement;
        this.payPeriod = payPeriod;
        this.amount = amount;
        this.status = BulkSettlementItemStatus.PENDING;
    }

    public void assignTransferId(String transferId) {
        this.transferId = transferId;
        this.status = BulkSettlementItemStatus.COMPLETED;
    }

    public void markPendingInquiry(String messageNo) {
        this.pendingMessageNo = messageNo;
        this.status = BulkSettlementItemStatus.PENDING_INQUIRY;
    }

    public void markFailed() {
        this.status = BulkSettlementItemStatus.FAILED;
    }

    public Long getWorkerId(){
        return payPeriod.getWorkerId();
    }
}
