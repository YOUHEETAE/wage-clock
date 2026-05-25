package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "ewa_requests")
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
        FAILED
    }

    public void approved(){
        this.status = EwaRequestStatus.APPROVED;
    }
    public void rejected(){
        this.status = EwaRequestStatus.REJECTED;
    }
    public void failed(){
        this.status = EwaRequestStatus.FAILED;
    }

    public Long getEmployerId(){
        return workSession.getEmployment().getEmployer().getId();
    }
    public Employer getEmployer() {
        return this.workSession.getEmployment().getEmployer();
    }

    public String getEmployerName() {
        return this.workSession.getEmployment().getEmployer().getName();
    }

}
