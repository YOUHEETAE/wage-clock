package com.wageclock.wageclock.domain.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboards")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "고용주 대시보드 조회 (사업장별 근무 현황)")
    @GetMapping("/{employmentId}")
    public DashboardResponse getDashboard(@PathVariable Long employmentId,
                                          @AuthenticationPrincipal Long employerId) {
        return dashboardService.getDashboard(employmentId, employerId);
    }
}
