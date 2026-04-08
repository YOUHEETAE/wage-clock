package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ewa_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class EwaRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_session_id")
    private WorkSession workSession;

    @Column(nullable = false)
    private BigDecimal requestedAmount;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EwaRequestStatus status;

    @Builder
    public EwaRequest(WorkSession workSession, BigDecimal requestedAmount, String idempotencyKey){
        this.workSession = workSession;
        this.requestedAmount = requestedAmount;
        this.idempotencyKey = idempotencyKey;
        this.status = EwaRequestStatus.PENDING;
    }

    public enum EwaRequestStatus{
        PENDING,
        APPROVED,
        REJECTED,
        PAID
    }
}
