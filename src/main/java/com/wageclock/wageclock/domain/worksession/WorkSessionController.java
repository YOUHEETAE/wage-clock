package com.wageclock.wageclock.domain.worksession;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/worksession")
public class WorkSessionController {

    private final WorkSessionService workSessionService;
    public WorkSessionController(WorkSessionService workSessionService) {
        this.workSessionService = workSessionService;
    }

    @PostMapping("/clockIn")
    public ClockInResponse clockIn (@RequestBody ClockInRequest clockInRequest,
                                    @AuthenticationPrincipal Long workerId) {
        return workSessionService.clockIn(clockInRequest, workerId);
    }
    @PostMapping("/clockOut")
    public ClockOutResponse clockOut (@RequestBody ClockOutRequest clockOutRequest,
                                      @AuthenticationPrincipal Long workerId) {
        return workSessionService.clockOut(clockOutRequest, workerId);
    }
}
