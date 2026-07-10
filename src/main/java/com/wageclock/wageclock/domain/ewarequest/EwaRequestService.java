package com.wageclock.wageclock.domain.ewarequest;

import com.wageclock.wageclock.domain.ewatransfer.EwaTransferService;
import org.springframework.stereotype.Service;

@Service
public class EwaRequestService {

    private final EwaRequestProcessor ewaRequestProcessor;
    private final EwaTransferService ewaTransferService;

    public EwaRequestService(EwaRequestProcessor ewaRequestProcessor, EwaTransferService ewaTransferService) {
        this.ewaRequestProcessor = ewaRequestProcessor;
        this.ewaTransferService = ewaTransferService;
    }

    public EwaResponseDto requestEwa(EwaRequestDto ewaRequestDto, Long workerId){
        return ewaRequestProcessor.processEwaRequest(ewaRequestDto, workerId);
    }

    public InitiateEwaResponse initiateEwa(Long ewaRequestId, Long employerId){
        EwaRequest ewaRequest = ewaRequestProcessor.validateAndMarkProcessing(ewaRequestId, employerId);
        EwaRequest.EwaRequestStatus status = ewaTransferService.processTransfer(ewaRequest);
        return new InitiateEwaResponse(ewaRequestId, ewaRequest.getRequestedAmount(), status);
    }

    public EwaResponseDto rejectEwa(Long ewaRequestId, Long employerId){
        return ewaRequestProcessor.validateAndRejectEwa(ewaRequestId, employerId);
    }
}