package com.wageclock.wageclock.domain.worksession;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public void pause(@RequestBody ClockOutRequest clockOutRequest,
                      @AuthenticationPrincipal Long workerId){
        Long sessionId = clockOutRequest.sessionId();
        workSessionService.pause(sessionId, workerId);
    }
    @Operation(summary = "근무 재개")
    @PostMapping("/resume")
    public void resume(@RequestBody ClockOutRequest clockOutRequest,
                       @AuthenticationPrincipal Long workerId){
        Long sessionId = clockOutRequest.sessionId();
        workSessionService.resume(sessionId, workerId);
    }
}
