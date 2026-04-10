package com.wageclock.wageclock.domain.worksession;

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
    public ClockInResponse clockIn (@RequestBody ClockInRequest clockInRequest){
        return workSessionService.clockIn(clockInRequest);
    }
    @PostMapping("/clockOut")
    public ClockOutResponse clockOut (@RequestBody ClockOutRequest clockOutRequest){
        return workSessionService.clockOut(clockOutRequest);
    }
}
