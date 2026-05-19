package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "ewa_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EwaTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name ="ewa_request_id", nullable = false)
    private EwaRequest ewaRequest;

    @Column(nullable = false)
    private BigDecimal amount;

    public EwaTransaction(EwaRequest ewaRequest, BigDecimal amount) {
        this.ewaRequest = ewaRequest;
        this.amount = amount;
    }
}
