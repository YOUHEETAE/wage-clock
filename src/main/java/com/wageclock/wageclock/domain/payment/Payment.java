package com.wageclock.wageclock.domain.payment;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.ewa.EwaRequest;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
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

    @Column(nullable = false,  unique = true)
    private String portOnePaymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "employer_id")
    private Employer employer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "ewa_request_id")
    private EwaRequest ewaRequest;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column()
    private String bank;

    @Column()
    private String accountNumber;

    @Column()
    private String expiredAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL)
    List<PaymentHistory> histories = new ArrayList<>();

    public enum PaymentStatus{
        READY,
        COMPLETED,
        FAILED,
        PROCESSING
    }

    @Builder
    public Payment (String portOnePaymentId, Employer employer, EwaRequest ewaRequest, BigDecimal amount,
                    String bank, String accountNumber, String expiredAt) {
        this.portOnePaymentId = portOnePaymentId;
        this.employer = employer;
        this.ewaRequest = ewaRequest;
        this.amount = amount;
        this.bank = bank;
        this.accountNumber = accountNumber;
        this.expiredAt = expiredAt;
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


    public PayPeriod getPayPeriod() {
        return ewaRequest.getPayPeriod();
    }

    public void updateVirtualAccountInfo(String bank, String accountNumber, String expiredAt){
        this.bank = bank;
        this.accountNumber = accountNumber;
        this.expiredAt = expiredAt;
    }
}
