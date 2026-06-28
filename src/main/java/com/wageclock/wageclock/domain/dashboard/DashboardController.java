package com.wageclock.wageclock.domain.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboards")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }
    @Operation(summary = "고용주 대시보드 조회 (직원별 최신 근무 현황)")
    @GetMapping
    public List<DashboardResponse> getDashboards(@AuthenticationPrincipal Long employerId) {
        return dashboardService.getDashboards(employerId);
    }
}
