package com.wageclock.wageclock.domain.workplace;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WorkplaceServiceTest {

    @Mock private WorkplaceRepository workplaceRepository;
    @Mock private EmployerRepository employerRepository;
    @Mock private EmploymentRepository employmentRepository;

    @InjectMocks
    private WorkplaceService workplaceService;

    @Test
    void employer_없음_예외() {
        when(employerRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
                () -> workplaceService.createWorkplace(new WorkplaceRequest("스타벅스", null), 1L));
    }

    @Test
    void 정상_사업장_생성() {
        Employer employer = Employer.builder().name("김사장").email("e@test.com").password("pw").build();
        Workplace saved = mock(Workplace.class);
        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));
        when(workplaceRepository.save(any())).thenReturn(saved);
        when(saved.getId()).thenReturn(10L);
        when(saved.getName()).thenReturn("스타벅스 강남점");
        when(saved.getAddress()).thenReturn("서울 강남구");

        WorkplaceResponse response = workplaceService.createWorkplace(
                new WorkplaceRequest("스타벅스 강남점", "서울 강남구"), 1L);

        assertEquals(10L, response.workplaceId());
        assertEquals("스타벅스 강남점", response.name());
        assertEquals("서울 강남구", response.address());
    }

    @Test
    void 고용주_사업장_목록_조회() {
        Workplace w1 = mock(Workplace.class);
        Workplace w2 = mock(Workplace.class);
        when(w1.getId()).thenReturn(1L);
        when(w1.getName()).thenReturn("스타벅스 강남점");
        when(w1.getAddress()).thenReturn(null);
        when(w2.getId()).thenReturn(2L);
        when(w2.getName()).thenReturn("맥도날드 서초점");
        when(w2.getAddress()).thenReturn("서울 서초구");
        when(workplaceRepository.findAllByEmployer_Id(1L)).thenReturn(List.of(w1, w2));

        List<WorkplaceResponse> result = workplaceService.getWorkplaces(1L);

        assertEquals(2, result.size());
        assertEquals("스타벅스 강남점", result.get(0).name());
        assertEquals("맥도날드 서초점", result.get(1).name());
    }

    @Test
    void 사업장_없으면_빈_목록_반환() {
        when(workplaceRepository.findAllByEmployer_Id(1L)).thenReturn(List.of());
        assertEquals(0, workplaceService.getWorkplaces(1L).size());
    }

    @Test
    void 워커_사업장_목록_조회_employmentId_포함() {
        Employment e1 = mock(Employment.class);
        Employment e2 = mock(Employment.class);
        when(e1.getId()).thenReturn(10L);
        when(e1.getWorkplaceId()).thenReturn(1L);
        when(e1.getWorkplaceName()).thenReturn("스타벅스 강남점");
        when(e1.getWorkplaceAddress()).thenReturn(null);
        when(e2.getId()).thenReturn(20L);
        when(e2.getWorkplaceId()).thenReturn(2L);
        when(e2.getWorkplaceName()).thenReturn("맥도날드 서초점");
        when(e2.getWorkplaceAddress()).thenReturn("서울 서초구");
        when(employmentRepository.findByWorker_Id(5L)).thenReturn(List.of(e1, e2));

        List<WorkerWorkplaceResponse> result = workplaceService.getWorkerWorkplaces(5L);

        assertEquals(2, result.size());
        assertEquals(10L, result.get(0).employmentId());
        assertEquals(1L, result.get(0).workplaceId());
        assertEquals("스타벅스 강남점", result.get(0).name());
        assertEquals(20L, result.get(1).employmentId());
    }

    @Test
    void 워커_고용_없으면_빈_목록_반환() {
        when(employmentRepository.findByWorker_Id(5L)).thenReturn(List.of());
        assertEquals(0, workplaceService.getWorkerWorkplaces(5L).size());
    }
}
