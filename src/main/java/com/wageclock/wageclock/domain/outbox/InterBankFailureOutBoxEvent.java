package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "inter_bank_failure_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterBankFailureOutBoxEvent extends BaseEntity {
    private static final int MAX_RETRY = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String transferId;

    @Column(nullable = false)
    private String portOnePaymentId;

    @Column(nullable = false)
    private Long bulkSettlementId;

    private int retryCount;

    private LocalDateTime lastAttemptAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private InterBankFailureOutBoxEventStatus status;

    public enum InterBankFailureOutBoxEventStatus {
        PENDING,
        PROCESSED,
        FAILED
    }

    @Builder
    public InterBankFailureOutBoxEvent(String transferId, String portOnePaymentId,
                                       Long bulkSettlementId) {
        this.transferId = transferId;
        this.portOnePaymentId = portOnePaymentId;
        this.bulkSettlementId = bulkSettlementId;
        this.status = InterBankFailureOutBoxEventStatus.PENDING;
    }

    public void incrementRetryCount() {
        this.retryCount++;
        this.lastAttemptAt = LocalDateTime.now();
        if (this.retryCount >= MAX_RETRY) {
            this.status = InterBankFailureOutBoxEventStatus.FAILED;
        }
    }
    public void processed() {
        this.status = InterBankFailureOutBoxEventStatus.PROCESSED;
    }
}
