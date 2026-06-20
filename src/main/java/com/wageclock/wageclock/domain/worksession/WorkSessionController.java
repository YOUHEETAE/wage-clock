package com.wageclock.wageclock.domain.worksession;

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

    @PostMapping("/clock-in")
    public ClockInResponse clockIn (@RequestBody ClockInRequest clockInRequest,
                                    @AuthenticationPrincipal Long workerId) {
        return workSessionService.clockIn(clockInRequest, workerId);
    }
    @PostMapping("/clock-out")
    public ClockOutResponse clockOut (@RequestBody ClockOutRequest clockOutRequest,
                                      @AuthenticationPrincipal Long workerId) {
        return workSessionService.clockOut(clockOutRequest, workerId);
    }
    @PostMapping("/pause")
    public void pause(@RequestBody ClockOutRequest clockOutRequest,
                      @AuthenticationPrincipal Long workerId){
        Long sessionId = clockOutRequest.sessionId();
        workSessionService.pause(sessionId, workerId);
    }
    @PostMapping("/resume")
    public void resume(@RequestBody ClockOutRequest clockOutRequest,
                       @AuthenticationPrincipal Long workerId){
        Long sessionId = clockOutRequest.sessionId();
        workSessionService.resume(sessionId, workerId);
    }
}
