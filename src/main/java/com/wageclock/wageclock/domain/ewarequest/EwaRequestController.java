package com.wageclock.wageclock.domain.ewarequest;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ewa-requests")
public class EwaRequestController {

    private final EwaRequestService ewaRequestService;
    public EwaRequestController(EwaRequestService ewaRequestService) {
        this.ewaRequestService = ewaRequestService;
    }

    @Operation(summary = "선지급(EWA) 요청")
    @PostMapping("/request")
    public EwaResponseDto request(@RequestBody EwaRequestDto requestDto,
                                  @AuthenticationPrincipal Long workerId) {
        return ewaRequestService.requestEwa(requestDto, workerId);
    }
    @Operation(summary = "선지급 승인 (고용주가 승인 시 펌뱅킹 이체 시작)")
    @PostMapping("/{ewaRequestId}/initiate")
    public InitiateEwaResponse initiate(@PathVariable Long ewaRequestId,
                                  @AuthenticationPrincipal Long employerId) {
        return ewaRequestService.initiateEwa(ewaRequestId, employerId);
    }
    @Operation(summary = "선지급 거절")
    @PostMapping("/{ewaRequestId}/reject")
    public EwaResponseDto reject(@PathVariable Long ewaRequestId,
                                 @AuthenticationPrincipal Long employerId) {
        return ewaRequestService.rejectEwa(ewaRequestId, employerId);
    }
    @Operation(summary = "승인 대기 중인 선지급 요청 목록")
    @GetMapping("/pending")
    public List<PendingEwaResponse> getPendingEwaRequests(@RequestParam Long workplaceId,
                                                      @AuthenticationPrincipal Long employerId) {
        return ewaRequestService.getPendingEwaRequests(workplaceId, employerId);
    }
}
