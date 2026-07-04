package com.wageclock.wageclock.domain.dashboard;

import com.wageclock.wageclock.domain.workplace.Workplace;
import com.wageclock.wageclock.domain.workplace.WorkplaceRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardService {

    private final DashboardRepository dashboardRepository;
    private final WorkplaceRepository workplaceRepository;

    public DashboardService(DashboardRepository dashboardRepository, WorkplaceRepository workplaceRepository) {
        this.dashboardRepository = dashboardRepository;
        this.workplaceRepository = workplaceRepository;
    }

    public List<DashboardResponse> getDashboard(Long workplaceId, Long employerId) {
        Workplace workplace = workplaceRepository.findById(workplaceId)
                .orElseThrow(() -> new NotFoundException("workplace not found"));
        if (!workplace.getEmployerId().equals(employerId)) {
            throw new UnauthorizedException("Unauthorized");
        }
        return dashboardRepository.getDashboard(workplaceId);
    }
}
