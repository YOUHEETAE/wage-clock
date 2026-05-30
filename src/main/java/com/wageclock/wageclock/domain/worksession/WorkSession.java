package com.wageclock.wageclock.domain.worksession;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class WorkSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employment_id", nullable = false)
    private Employment employment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pay_period_id", nullable = false)
    private PayPeriod payPeriod;

    @Column(nullable = false)
    private BigDecimal hourlyWage;

    @Column(nullable = false)
    private LocalDateTime clockIn;

    @Column
    private LocalDateTime clockOut;

    @Column(nullable = false)
    private BigDecimal earnedAmount;

    @Column(nullable = false)
    private LocalDateTime lastResumeAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkSessionStatus status;

    @Builder
    public WorkSession(Employment employment, PayPeriod payPeriod, LocalDateTime clockIn){
        this.employment = employment;
        this.payPeriod = payPeriod;
        this.clockIn = clockIn;
        this.hourlyWage = employment.getHourlyWage();
        this.earnedAmount = BigDecimal.ZERO;
        this.lastResumeAt = LocalDateTime.now();
        this.status = WorkSessionStatus.WORKING;
    }

    public enum WorkSessionStatus {
        WORKING,
        PAUSED,
        COMPLETED
    }

    public void clockOut(){
        if(isCompleted()){
            throw new IllegalStateException("Work session has already been closed");
        }
        this.clockOut = LocalDateTime.now();
        this.earnedAmount = getCurrentEarnedAmount();
        this.status = WorkSessionStatus.COMPLETED;
    }

    public boolean isCompleted() {
        return status == WorkSessionStatus.COMPLETED;
    }

    public boolean isPaused(){ return status == WorkSessionStatus.PAUSED; }

    public boolean isWorking(){
        return status == WorkSessionStatus.WORKING;
    }

    public BigDecimal getCurrentEarnedAmount() {
        if(isCompleted() || isPaused()) return this.earnedAmount;
        BigDecimal seconds = BigDecimal.valueOf(Duration.between(lastResumeAt, LocalDateTime.now()).toSeconds());
        return this.earnedAmount.add(hourlyWage.multiply(seconds).divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP));
    }

    public Long getWorkerId(){
        return employment.getWorker().getId();
    }

    public void pause(){
        this.earnedAmount = getCurrentEarnedAmount();
        this.status = WorkSessionStatus.PAUSED;
    }
    public void resume(){
        this.status = WorkSessionStatus.WORKING;
        this.lastResumeAt = LocalDateTime.now();
    }

}
