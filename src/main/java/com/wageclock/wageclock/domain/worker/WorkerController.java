package com.wageclock.wageclock.domain.worker;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workers")
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @Operation(summary = "근로자 계좌 정보 등록 (펌뱅킹 송금용)")
    @PostMapping("/register-account-info")
    public void registerPartner(@AuthenticationPrincipal Long workerId,
                                @RequestBody RegisterAccountInfo registerAccountInfo) {
        workerService.registerAccountInfo(workerId, registerAccountInfo);
    }
}
