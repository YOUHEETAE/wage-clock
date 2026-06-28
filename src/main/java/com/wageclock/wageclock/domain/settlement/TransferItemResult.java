package com.wageclock.wageclock.domain.settlement;

public sealed interface TransferItemResult permits TransferItemResult.Success,
        TransferItemResult.PendingInquiry,
        TransferItemResult.Fail,
        TransferItemResult.Unknown,
        TransferItemResult.Retryable{
    record Success(Long itemId, String transferId) implements TransferItemResult {}
    record PendingInquiry(Long itemId, String messageNo) implements TransferItemResult {}
    record Fail(Long itemId, String failureReason) implements TransferItemResult {}
    record Unknown(Long itemId) implements TransferItemResult{}
    record Retryable(Long itemId) implements TransferItemResult{}
}
