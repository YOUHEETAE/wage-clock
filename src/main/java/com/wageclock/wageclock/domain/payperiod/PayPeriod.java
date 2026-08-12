package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pay_periods")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayPeriod extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "employment_id")
    private Employment employment;

    @OneToMany(mappedBy = "payPeriod", fetch = FetchType.LAZY)
    private List<WorkSession> workSessions = new ArrayList<>();

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column
    private LocalDate periodEnd;

    @Column(nullable = false)
    private BigDecimal totalEarnedAmount;

    @Column(nullable = false)
    private BigDecimal totalEwaAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayPeriodStatus status;

    public enum PayPeriodStatus {
        ACTIVE,
        CLOSED
    }

    public PayPeriod(Employment employment) {
        this.employment = employment;
        this.periodStart = LocalDate.now();
        this.totalEarnedAmount = BigDecimal.ZERO;
        this.totalEwaAmount = BigDecimal.ZERO;
        this.status = PayPeriodStatus.ACTIVE;
    }

    public void close() {
        this.status = PayPeriodStatus.CLOSED;
        this.periodEnd = LocalDate.now();
    }
    public void addEarnedAmount(BigDecimal amount) {
        this.totalEarnedAmount = this.totalEarnedAmount.add(amount);
    }
    /**
     * 선지급 누적액 계상 규칙 — 요청 시점에 한 번 더하고, 확정 실패일 때만 뺀다.
     * 성공과 미확정 구간에서는 건드리지 않는다.
     *
     *   요청           + amount   EwaRequestProcessor.processEwaRequest
     *   거절           - amount   EwaRequestProcessor.validateAndRejectEwa
     *   이체 실패 확정   - amount   EwaTransferProcessor.failTransfer / failRetry
     *   이체 성공         -        (요청 시점에 이미 반영됨)
     *   불능통지·UNKNOWN  -        (미확정이므로 한도를 계속 잡아둔다)
     *
     * 요청 시점에 잡아두는 이유는 승인 대기와 이체 진행 중에도 한도가 소진된
     * 것으로 보여야 하기 때문이다. 성공 시점에 더하면 결과를 모르는 구간에
     * 한도가 비어 근로자가 한도를 초과해 요청할 수 있다.
     */
    public void addEwaAmount(BigDecimal amount) {
        this.totalEwaAmount = this.totalEwaAmount.add(amount);
    }
    public void subtractEwaAmount(BigDecimal amount) {
        this.totalEwaAmount = this.totalEwaAmount.subtract(amount);
    }
    public BigDecimal getRemainingEwaLimit() {
        return totalEarnedAmount.multiply(BigDecimal.valueOf(0.3)).subtract(this.totalEwaAmount);
    }
    public Long getWorkerId(){
        return employment.getWorkerId();
    }
    public BigDecimal getRemainingEwaLimitWith(BigDecimal additionalEarned) {
        return totalEarnedAmount.add(additionalEarned).multiply(BigDecimal.valueOf(0.3)).subtract(totalEwaAmount);
    }
    public BigDecimal getActualPayAmount(){
        return this.totalEarnedAmount.subtract(this.totalEwaAmount);
    }
    public Long getEmployerId(){
        return employment.getEmployer().getId();
    }
    public String getEmployerName(){
        return employment.getEmployer().getName();
    }
    public Long getEmploymentId() {
        return employment.getId();
    }
    public String getWorkerName() {
        return employment.getWorker().getName();
    }

}
