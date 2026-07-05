package com.wageclock.wageclock.domain.worksession;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/work-sessions")
public class WorkSessionController {

    private final WorkSessionService workSessionService;
    public WorkSessionController(WorkSessionService workSessionService) {
        this.workSessionService = workSessionService;
    }

    @Operation(summary = "출근 (급여시계 시작)")
    @PostMapping("/clock-in")
    public ClockInResponse clockIn (@RequestBody ClockInRequest clockInRequest,
                                    @AuthenticationPrincipal Long workerId) {
        return workSessionService.clockIn(clockInRequest, workerId);
    }
    @Operation(summary = "퇴근 (급여 확정)")
    @PostMapping("/clock-out")
    public ClockOutResponse clockOut (@RequestBody ClockOutRequest clockOutRequest,
                                      @AuthenticationPrincipal Long workerId) {
        return workSessionService.clockOut(clockOutRequest, workerId);
    }
    @Operation(summary = "근무 일시정지")
    @PostMapping("/pause")
    public PauseResponse pause(@RequestBody ClockOutRequest clockOutRequest,
                               @AuthenticationPrincipal Long workerId){
        return workSessionService.pause(clockOutRequest.sessionId(), workerId);
    }
    @Operation(summary = "현재 진행 중인 세션 조회 (WORKING / PAUSED)")
    @GetMapping("/current")
    public ResponseEntity<CurrentSessionResponse> getCurrentSession(
            @RequestParam Long employmentId,
            @AuthenticationPrincipal Long workerId) {
        return workSessionService.getCurrentSession(employmentId, workerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @Operation(summary = "근무 재개")
    @PostMapping("/resume")
    public ResumeResponse resume(@RequestBody ClockOutRequest clockOutRequest,
                                 @AuthenticationPrincipal Long workerId){
        return workSessionService.resume(clockOutRequest.sessionId(), workerId);
    }
}
