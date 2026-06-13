package com.wageclock.wageclock.domain.worker;

import com.wageclock.wageclock.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "workers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Worker extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false,  unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String accountNumber;

    private String bankCode;

    private String accountHolder;


    @Builder
    public Worker(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }
    public void registerAccountInfo(String accountNumber, String bankCode, String accountHolder) {
        this.accountNumber = accountNumber;
        this.bankCode = bankCode;
        this.accountHolder = accountHolder;
    }
}
