package com.wageclock.wageclock.domain.worksession;

import com.wageclock.wageclock.domain.employment.Employment;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class WorkSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employment_id", nullable = false)
    private Employment employment;

    @Column(nullable = false)
    private BigDecimal hourlyWage;

    @Column(nullable = false)
    private LocalDateTime clockIn;

    @Column
    private LocalDateTime clockOut;

    @Column(nullable = false)
    private BigDecimal earnedAmount;

    @Column(nullable = false)
    private BigDecimal totalEwaAmount;

    @Column(nullable = false)
    private Long pausedDuration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkSessionStatus status;

    @Builder
    public WorkSession(Employment employment, LocalDateTime clockIn){
        this.employment = employment;
        this.clockIn = clockIn;
        this.hourlyWage = employment.getHourlyWage();
        this.earnedAmount = BigDecimal.ZERO;
        this.totalEwaAmount = BigDecimal.ZERO;
        this.pausedDuration = 0L;
        this.status = WorkSessionStatus.WORKING;
    }

    public enum WorkSessionStatus {
        WORKING,
        PAUSED,
        COMPLETED
    }
}
