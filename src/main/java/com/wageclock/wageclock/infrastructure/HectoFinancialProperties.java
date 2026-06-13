package com.wageclock.wageclock.infrastructure;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "hectofinancial")
public class HectoFinancialProperties {
    private final String companyNo;
    private final String bankCode;
    private final String accountNo;
    private final String accountPwd;
    private final String reconfirmCode;

    public HectoFinancialProperties(String companyNo, String bankCode, String accountNo,
                                    String accountPwd, String reconfirmCode) {
        this.companyNo = companyNo;
        this.bankCode = bankCode;
        this.accountNo = accountNo;
        this.accountPwd = accountPwd;
        this.reconfirmCode = reconfirmCode;
    }
}
