package com.wageclock.wageclock.domain.employment;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "employment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Employment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id", nullable = false)
    private Employer employer;

    @Column(nullable = false)
    private BigDecimal hourlyWage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmploymentStatus status;

    @Builder
    public Employment(Worker worker, Employer employer, BigDecimal hourlyWage) {
        this.worker = worker;
        this.employer = employer;
        this.hourlyWage = hourlyWage;
        this.status = EmploymentStatus.ACTIVE;
    }

    public enum EmploymentStatus {
        ACTIVE,
        TERMINATED,
        SUSPENDED
    }
}
