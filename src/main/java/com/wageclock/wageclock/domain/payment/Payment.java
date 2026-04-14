package com.wageclock.wageclock.domain.payment;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.ewa.EwaRequest;
import com.wageclock.wageclock.domain.worksession.WorkSession;
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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "payment")
public class Payment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "employer_id")
    private Employer employer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "ewa_request_id")
    private EwaRequest ewaRequest;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL)
    List<PaymentHistory> histories = new ArrayList<>();

    public enum PaymentStatus{
        READY,
        COMPLETED,
        FAILED,
        PROCESSING,
        UNKNOWN
    }

    @Builder
    public Payment (Employer employer, EwaRequest ewaRequest, BigDecimal amount, String idempotencyKey) {
        this.employer = employer;
        this.ewaRequest = ewaRequest;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.READY;
        this.histories.add(new PaymentHistory(this, this.amount, PaymentStatus.READY));
    }

    public void changeStatus(PaymentStatus newStatus) {
        this.status = newStatus;
        this.histories.add(new PaymentHistory(this, this.amount, newStatus));
    }

    public void completed() { changeStatus(PaymentStatus.COMPLETED); }
    public void failed() { changeStatus(PaymentStatus.FAILED); }
    public void processing() { changeStatus(PaymentStatus.PROCESSING); }
    public void unknown() { changeStatus(PaymentStatus.UNKNOWN); }


    public WorkSession getWorkSession(){
        return this.ewaRequest.getWorkSession();
    }

}
