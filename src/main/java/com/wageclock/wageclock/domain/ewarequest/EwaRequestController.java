package com.wageclock.wageclock.domain.ewa;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ewa-requests")
public class EwaRequestController {

    private final EwaRequestService ewaRequestService;
    public EwaRequestController(EwaRequestService ewaRequestService) {
        this.ewaRequestService = ewaRequestService;
    }

    @PostMapping("/request")
    public EwaResponseDto request(@RequestBody EwaRequestDto requestDto,
                                  @AuthenticationPrincipal Long workerId) {
        return ewaRequestService.requestEwa(requestDto, workerId);
    }
    @PostMapping("/{ewaRequestId}/initiate")
    public InitiateEwaResponse initiate(@PathVariable Long ewaRequestId,
                                  @AuthenticationPrincipal Long employerId) {
        return ewaRequestService.initiateEwa(ewaRequestId, employerId);
    }
    @PostMapping("/{ewaRequestId}/reject")
    public EwaResponseDto reject(@PathVariable Long ewaRequestId,
                                 @AuthenticationPrincipal Long employerId) {
        return ewaRequestService.rejectEwa(ewaRequestId, employerId);
    }
}
