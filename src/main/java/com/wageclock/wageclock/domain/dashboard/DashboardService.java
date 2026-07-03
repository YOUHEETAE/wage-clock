package com.wageclock.wageclock.domain.dashboard;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final DashboardRepository dashboardRepository;
    private final EmploymentRepository employmentRepository;

    public DashboardService(DashboardRepository dashboardRepository, EmploymentRepository employmentRepository) {
        this.dashboardRepository = dashboardRepository;
        this.employmentRepository = employmentRepository;
    }

    public DashboardResponse getDashboard(Long employmentId, Long employerId) {
        Employment employment = employmentRepository.findById(employmentId)
                .orElseThrow(() -> new NotFoundException("Employment not found"));
        if (!employment.getEmployerId().equals(employerId)) {
            throw new UnauthorizedException("Unauthorized");
        }
        return dashboardRepository.getDashboard(employmentId);
    }
}
