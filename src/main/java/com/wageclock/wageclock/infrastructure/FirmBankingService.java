package com.wageclock.wageclock.infrastructure;

import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.global.exception.ExternalApiException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
public class FirmBankingService {

    private static final String PROCESSING_CODE = "PRCS";
    private static final int MAX_INQUIRY_RETRY = 5;

    private final HectoFinancialProperties properties;
    private final StringRedisTemplate redisTemplate;

    public FirmBankingService(HectoFinancialProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    public HectoFinancialTransferResponse transfer(Worker worker, BigDecimal amount, String referenceId) {
        String messageNo = generateMessageNo();
        LocalDateTime now = LocalDateTime.now();

        HectoFinancialTransferRequest request = new HectoFinancialTransferRequest(
                properties.getCompanyNo(),
                properties.getBankCode(),
                messageNo,
                now.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                now.format(DateTimeFormatter.ofPattern("HHmmss")),
                properties.getAccountNo(),
                properties.getAccountPwd(),
                properties.getReconfirmCode(),
                amount.longValue(),
                worker.getBankCode(),
                worker.getAccountNumber(),
                worker.getName()
        );

        // TODO: 실제 연동 시 TCP 소켓으로 교체 (핵토파이낸셜 2000/100 전문)
        // 타행이체 정상응답 후 3000/100(타행이체불능통지) 수신 처리 필요
        return send(request);
    }

    public HectoFinancialInquiryResponse inquireTransferResult(String originalMessageNo) {
        for (int i = 0; i < MAX_INQUIRY_RETRY; i++) {
            // TODO: 실제 연동 시 TCP 소켓으로 교체 (핵토파이낸셜 7000/100 전문)
            HectoFinancialInquiryResponse response = sendInquiry(originalMessageNo);
            if (!PROCESSING_CODE.equals(response.processResult())) {
                return response;
            }
        }
        // MAX_INQUIRY_RETRY 초과 시 처리 중 상태로 반환 → 스케줄러가 다음 사이클에 재시도
        return new HectoFinancialInquiryResponse("0000", PROCESSING_CODE, "0000000000000", "0000000000000");
    }

    private HectoFinancialTransferResponse send(HectoFinancialTransferRequest request) {
        // TODO: 실제 연동 시 TCP 소켓으로 교체
        return new HectoFinancialTransferResponse("0000", request.messageNo());
    }

    private HectoFinancialInquiryResponse sendInquiry(String originalMessageNo) {
        // TODO: 실제 연동 시 TCP 소켓으로 교체
        return new HectoFinancialInquiryResponse("0000", "0000", "0000000000000", "0000000000000");
    }

    private String generateMessageNo() {
        String key = "hectofinancial:seq:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long seq = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 1, TimeUnit.DAYS);
        return String.format("%06d", seq);
    }
}