package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bulk_settlements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class BulkSettlement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long employerId;

    @Column(nullable = false, unique = true)
    private String portOnePaymentId;

    @OneToMany(mappedBy = "bulkSettlement", cascade = CascadeType.ALL)
    private List<BulkSettlementItem> items = new ArrayList<>();

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BulkSettlementStatus status;

    private String bank;

    private String accountNumber;

    private String expiredAt;


    public enum BulkSettlementStatus {
        READY,
        PROCESSING,
        COMPLETED,
        TRANSFER_FAILED,
        PAYMENT_FAILED
    }

    @Builder
    public BulkSettlement(Long employerId, String portOnePaymentId,
                          BigDecimal totalAmount) {
        this.employerId = employerId;
        this.portOnePaymentId = portOnePaymentId;
        this.totalAmount = totalAmount;
        this.status = BulkSettlementStatus.READY;
    }
    public void addItem(BulkSettlementItem item) {
        this.items.add(item);
    }

    public void updateAccountInfo(String bank, String accountNumber, String expiredAt) {
        this.bank = bank;
        this.accountNumber = accountNumber;
        this.expiredAt = expiredAt;
    }

    public void processing(){
        this.status = BulkSettlementStatus.PROCESSING;
    }

    public void completed(){
        this.status = BulkSettlementStatus.COMPLETED;
    }
    public void transferFailed(){
        this.status = BulkSettlementStatus.TRANSFER_FAILED;
    }
    public void paymentFailed(){
        this.status = BulkSettlementStatus.PAYMENT_FAILED;
    }

}
