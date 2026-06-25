package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "bulk_settlement_items")
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

    private String messageNo;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BulkSettlementItemStatus status;


    public enum BulkSettlementItemStatus {
        PENDING,
        PENDING_INQUIRY,
        COMPLETED,
        FAILED,
        RETRYING,
        UNKNOWN
    }

    @Builder
    public BulkSettlementItem(BulkSettlement bulkSettlement, PayPeriod payPeriod, BigDecimal amount) {
        this.bulkSettlement = bulkSettlement;
        this.payPeriod = payPeriod;
        this.amount = amount;
        this.status = BulkSettlementItemStatus.PENDING;
    }

    public void completed() {
        this.status = BulkSettlementItemStatus.COMPLETED;
    }

    public void markPendingInquiry() {
        this.status = BulkSettlementItemStatus.PENDING_INQUIRY;
    }

    public void failed() {
        this.status = BulkSettlementItemStatus.FAILED;
    }

    public void unknown() {
        this.status = BulkSettlementItemStatus.UNKNOWN;
    }

    public void assignMessageNo(String messageNo) {
        this.messageNo = messageNo;
    }

    public void retrying() {
        this.status = BulkSettlementItemStatus.RETRYING;
    }

    public Long getWorkerId(){
        return payPeriod.getWorkerId();
    }

}
