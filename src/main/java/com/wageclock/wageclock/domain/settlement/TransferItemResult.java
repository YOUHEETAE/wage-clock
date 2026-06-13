package com.wageclock.wageclock.domain.settlement;

public sealed interface TransferItemResult permits TransferItemResult.Success,
        TransferItemResult.PendingInquiry,
        TransferItemResult.Fail{
    record Success(Long itemId, String transferId) implements TransferItemResult {}
    record PendingInquiry(Long itemId, String messageNo) implements TransferItemResult {}
    record Fail(Long itemId) implements TransferItemResult {}
}
