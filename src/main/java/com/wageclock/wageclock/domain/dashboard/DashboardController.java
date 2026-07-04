package com.wageclock.wageclock.domain.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @Operation(summary = "고용주 대시보드 조회 (사업장 전체 워커 근무 현황)")
    @GetMapping("/{workplaceId}")
    public List<DashboardResponse> getDashboard(@PathVariable Long workplaceId,
                                                @AuthenticationPrincipal Long employerId) {
        return dashboardService.getDashboard(workplaceId, employerId);
    }
}
