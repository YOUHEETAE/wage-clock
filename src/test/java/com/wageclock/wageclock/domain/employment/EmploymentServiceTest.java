package com.wageclock.wageclock.domain.employment;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.domain.workplace.Workplace;
import com.wageclock.wageclock.domain.workplace.WorkplaceRepository;
import com.wageclock.wageclock.global.exception.DuplicateException;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EmploymentServiceTest {

    @Mock private EmploymentRepository employmentRepository;
    @Mock private WorkerRepository workerRepository;
    @Mock private EmployerRepository employerRepository;
    @Mock private WorkplaceRepository workplaceRepository;
    @Mock private Employment savedEmployment;
    @Mock private Worker worker;
    @Mock private Workplace workplace;

    @InjectMocks
    private EmploymentService employmentService;

    private Employer employer;

    @BeforeEach
    void setUp() {
        employer = Employer.builder().name("홍길동").email("employer@test.com").password("password").build();
    }

    @Test
    void employer_없음_예외() {
        when(employerRepository.findById(1L)).thenReturn(Optional.empty());
        EmploymentRequest request = new EmploymentRequest("worker@test.com", BigDecimal.valueOf(10000), 5L);
        assertThrows(NotFoundException.class, () -> employmentService.createEmployment(request, 1L));
    }

    @Test
    void worker_없음_예외() {
        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));
        when(workerRepository.findByEmail("worker@test.com")).thenReturn(Optional.empty());
        EmploymentRequest request = new EmploymentRequest("worker@test.com", BigDecimal.valueOf(10000), 5L);
        assertThrows(NotFoundException.class, () -> employmentService.createEmployment(request, 1L));
    }

    @Test
    void workplace_없음_예외() {
        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));
        when(workerRepository.findByEmail("worker@test.com")).thenReturn(Optional.of(worker));
        when(workplaceRepository.findById(5L)).thenReturn(Optional.empty());
        EmploymentRequest request = new EmploymentRequest("worker@test.com", BigDecimal.valueOf(10000), 5L);
        assertThrows(NotFoundException.class, () -> employmentService.createEmployment(request, 1L));
    }

    @Test
    void workplace_고용주_불일치_예외() {
        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));
        when(workerRepository.findByEmail("worker@test.com")).thenReturn(Optional.of(worker));
        when(workplaceRepository.findById(5L)).thenReturn(Optional.of(workplace));
        when(workplace.getEmployerId()).thenReturn(99L);
        EmploymentRequest request = new EmploymentRequest("worker@test.com", BigDecimal.valueOf(10000), 5L);
        assertThrows(UnauthorizedException.class, () -> employmentService.createEmployment(request, 1L));
    }

    @Test
    void 중복_employment_예외() {
        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));
        when(workerRepository.findByEmail("worker@test.com")).thenReturn(Optional.of(worker));
        when(workplaceRepository.findById(5L)).thenReturn(Optional.of(workplace));
        when(workplace.getEmployerId()).thenReturn(1L);
        when(worker.getId()).thenReturn(2L);
        when(employmentRepository.existsByEmployerIdAndWorkerId(1L, 2L)).thenReturn(true);
        EmploymentRequest request = new EmploymentRequest("worker@test.com", BigDecimal.valueOf(10000), 5L);
        assertThrows(DuplicateException.class, () -> employmentService.createEmployment(request, 1L));
    }

    @Test
    void 정상_employment_생성() {
        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));
        when(workerRepository.findByEmail("worker@test.com")).thenReturn(Optional.of(worker));
        when(workplaceRepository.findById(5L)).thenReturn(Optional.of(workplace));
        when(workplace.getEmployerId()).thenReturn(1L);
        when(worker.getId()).thenReturn(2L);
        when(employmentRepository.existsByEmployerIdAndWorkerId(1L, 2L)).thenReturn(false);
        when(employmentRepository.save(any())).thenReturn(savedEmployment);
        when(savedEmployment.getId()).thenReturn(10L);
        when(savedEmployment.getHourlyWage()).thenReturn(BigDecimal.valueOf(10000));
        when(savedEmployment.getWorkplaceId()).thenReturn(5L);
        when(savedEmployment.getWorkplaceName()).thenReturn("스타벅스 강남점");
        when(savedEmployment.getWorkplaceAddress()).thenReturn(null);

        EmploymentRequest request = new EmploymentRequest("worker@test.com", BigDecimal.valueOf(10000), 5L);
        EmploymentResponse response = employmentService.createEmployment(request, 1L);

        assertEquals(10L, response.employmentId());
        assertEquals(BigDecimal.valueOf(10000), response.hourlyWage());
        assertEquals(5L, response.workplaceId());
        assertEquals("스타벅스 강남점", response.workplaceName());
    }
}