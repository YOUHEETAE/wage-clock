package com.wageclock.wageclock.domain.dashboard;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DashboardServiceTest {

    @Mock
    private DashboardRepository dashboardRepository;
    @Mock
    private EmploymentRepository employmentRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void 정상_대시보드_조회() {
        Employment employment = mock(Employment.class);
        when(employment.getEmployerId()).thenReturn(1L);
        when(employmentRepository.findById(10L)).thenReturn(Optional.of(employment));
        when(dashboardRepository.getDashboard(10L)).thenReturn(mock(DashboardResponse.class));

        DashboardResponse result = dashboardService.getDashboard(10L, 1L);

        assertNotNull(result);
    }

    @Test
    void 고용_없음_예외() {
        when(employmentRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> dashboardService.getDashboard(10L, 1L));
    }

    @Test
    void 다른_고용주_접근_예외() {
        Employment employment = mock(Employment.class);
        when(employment.getEmployerId()).thenReturn(2L);
        when(employmentRepository.findById(10L)).thenReturn(Optional.of(employment));

        assertThrows(UnauthorizedException.class, () -> dashboardService.getDashboard(10L, 1L));
    }
}
