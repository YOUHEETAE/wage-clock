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
@Getter
@Table(name = "ewa_outbox_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EwaOutBoxEvent extends BaseEntity {
    private static final int MAX_RETRY = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ewaRequestId;

    @Column(nullable = false)
    private String portOnePaymentId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String employerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutBoxStatus status;

    private int retryCount;

    private LocalDateTime lastAttemptAt;

    public enum OutBoxStatus{
        PENDING,
        PROCESSED,
        FAILED
    }

    @Builder
    public EwaOutBoxEvent(Long ewaRequestId,  String portOnePaymentId,
                          BigDecimal amount, String employerName) {
        this.ewaRequestId = ewaRequestId;
        this.portOnePaymentId = portOnePaymentId;
        this.amount = amount;
        this.employerName = employerName;
        this.status = OutBoxStatus.PENDING;
        this.retryCount = 0;
    }

    public void processed(){
        this.status = OutBoxStatus.PROCESSED;
    }

    public void incrementRetryCount(){
        this.retryCount++;
        this.lastAttemptAt = LocalDateTime.now();
        if(this.retryCount >= MAX_RETRY){
            this.status = OutBoxStatus.FAILED;
        }
    }
}
