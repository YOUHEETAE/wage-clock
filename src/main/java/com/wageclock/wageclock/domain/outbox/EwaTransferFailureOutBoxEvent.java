package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ewa_transfer_failure_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EwaTransferFailureOutBoxEvent extends BaseEntity {
    static final int MAX_RETRY = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ewaTransferId;

    @Column(nullable = false)
    private String  messageNo;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private EwaTransferFailureOutBoxStatus status;

    private int retryCount;

    private LocalDateTime lastAttemptAt;

    public enum EwaTransferFailureOutBoxStatus {
        PENDING,
        PROCESSED,
        FAILED
    }
    @Builder
    public EwaTransferFailureOutBoxEvent(Long ewaTransferId, BigDecimal amount, String messageNo) {
        this.ewaTransferId = ewaTransferId;
        this.messageNo = messageNo;
        this.amount = amount;
        this.status = EwaTransferFailureOutBoxStatus.PENDING;
    }
    public void processed() {
        this.status = EwaTransferFailureOutBoxStatus.PROCESSED;
    }
    public void incrementRetryCount() {
        this.retryCount++;
        this.lastAttemptAt = LocalDateTime.now();
        if (this.retryCount >= MAX_RETRY) {
            this.status = EwaTransferFailureOutBoxStatus.FAILED;
        }
    }
    public void failed(){
        this.status = EwaTransferFailureOutBoxStatus.FAILED;
    }
}
