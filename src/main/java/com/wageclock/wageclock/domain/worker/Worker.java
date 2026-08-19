package com.wageclock.wageclock.domain.worker;

import com.wageclock.wageclock.domain.port.TransferAccount;
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
    /**
     * 이체 전문에는 로그인 이름(name)이 아니라 예금주명(accountHolder)을 싣는다.
     * 은행이 대조하는 값이 예금주명이라, 둘이 다르면 이체가 거부된다.
     * <p>
     * 반드시 트랜잭션 안에서 호출한다. 준영속 상태에서 부르면 지연 로딩으로 터진다.
     */
    public TransferAccount toTransferAccount() {
        return new TransferAccount(bankCode, accountNumber, accountHolder);
    }
}
