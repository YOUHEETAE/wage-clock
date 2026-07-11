package com.wageclock.wageclock.domain.ewarequest;

import com.wageclock.wageclock.domain.ewatransfer.EwaTransferService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EwaRequestService {

    private final EwaRequestProcessor ewaRequestProcessor;
    private final EwaTransferService ewaTransferService;
    private final EwaRequestRepository ewaRequestRepository;

    public EwaRequestService(EwaRequestProcessor ewaRequestProcessor, EwaTransferService ewaTransferService, EwaRequestRepository ewaRequestRepository) {
        this.ewaRequestProcessor = ewaRequestProcessor;
        this.ewaTransferService = ewaTransferService;
        this.ewaRequestRepository = ewaRequestRepository;
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
    public List<PendingEwaResponse> getPendingEwaRequests(Long workplaceId, Long employerId){
        return ewaRequestRepository.findPendingByWorkplace(workplaceId, employerId);
    }

    public List<EwaRequestDetailResponse> getMyRequests(Long employmentId, Long workerId){
        return ewaRequestRepository.findDetailsByEmploymentId(employmentId, workerId);
    }
}