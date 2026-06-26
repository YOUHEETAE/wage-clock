package com.wageclock.wageclock.domain.port;


public record WageTransferResult(String transferId, String pendingMessageNo, String failureReason) {
    public enum ResultType{
        SUCCESS,
        PENDING_INQUIRY,
        FAILURE,
        UNKNOWN
    }
    public ResultType classify(){
        if(transferId != null) return  ResultType.SUCCESS;
        if(pendingMessageNo != null) return  ResultType.PENDING_INQUIRY;
        if(failureReason != null) return  ResultType.FAILURE;
        return ResultType.UNKNOWN;
    }
}