package com.wageclock.wageclock.domain.ewatransfer;

/**
 * 결과 조회(7000/100)에 필요한 값만 담는다.
 * 조회는 기존 전문번호로 결과를 확인할 뿐이라 계좌가 필요 없다.
 */
public record EwaTransferInquiryContext(Long ewaTransferId, String messageNo) {
}
