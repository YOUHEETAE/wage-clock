package com.wageclock.wageclock.domain.dashboard;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardService {

    private final DashboardRepository dashboardRepository;

    public DashboardService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    public List<DashboardResponse> getDashboards(Long employerId){
        return dashboardRepository.getDashboards(employerId);
    }
}
