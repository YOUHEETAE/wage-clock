package com.wageclock.wageclock.domain.dashboard;

import com.wageclock.wageclock.domain.workplace.Workplace;
import com.wageclock.wageclock.domain.workplace.WorkplaceRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DashboardServiceTest {

    @Mock private DashboardRepository dashboardRepository;
    @Mock private WorkplaceRepository workplaceRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void 정상_대시보드_조회() {
        Workplace workplace = mock(Workplace.class);
        when(workplace.getEmployerId()).thenReturn(1L);
        when(workplaceRepository.findById(10L)).thenReturn(Optional.of(workplace));
        when(dashboardRepository.getDashboard(10L)).thenReturn(List.of(mock(DashboardResponse.class)));

        List<DashboardResponse> result = dashboardService.getDashboard(10L, 1L);

        assertEquals(1, result.size());
    }

    @Test
    void workplace_없음_예외() {
        when(workplaceRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> dashboardService.getDashboard(10L, 1L));
    }

    @Test
    void 다른_고용주_접근_예외() {
        Workplace workplace = mock(Workplace.class);
        when(workplace.getEmployerId()).thenReturn(2L);
        when(workplaceRepository.findById(10L)).thenReturn(Optional.of(workplace));

        assertThrows(UnauthorizedException.class, () -> dashboardService.getDashboard(10L, 1L));
    }
}
