package com.wageclock.wageclock.domain.employment;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.global.exception.DuplicateException;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EmploymentServiceTest {

    @Mock
    private EmploymentRepository employmentRepository;
    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private EmployerRepository employerRepository;
    @Mock
    private Employment savedEmployment;

    @InjectMocks
    private EmploymentService employmentService;

    private Employer employer;
    private Worker worker;

    @BeforeEach
    void setUp() {
        employer = Employer.builder().name("홍길동").email("employer@test.com").password("password").build();
        worker = Worker.builder().name("아무개").email("worker@test.com").password("password").build();
    }

    @Test
    void employer_없음_예외() {
        when(employerRepository.findById(1L)).thenReturn(Optional.empty());
        CreateEmploymentRequest request = new CreateEmploymentRequest(2L, BigDecimal.valueOf(10000));
        assertThrows(NotFoundException.class, () -> employmentService.createEmployment(request, 1L));
    }

    @Test
    void worker_없음_예외() {
        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));
        when(workerRepository.findById(2L)).thenReturn(Optional.empty());
        CreateEmploymentRequest request = new CreateEmploymentRequest(2L, BigDecimal.valueOf(10000));
        assertThrows(NotFoundException.class, () -> employmentService.createEmployment(request, 1L));
    }

    @Test
    void 중복_employment_예외() {
        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));
        when(workerRepository.findById(2L)).thenReturn(Optional.of(worker));
        when(employmentRepository.existsByEmployerIdAndWorkerId(1L, 2L)).thenReturn(true);
        CreateEmploymentRequest request = new CreateEmploymentRequest(2L, BigDecimal.valueOf(10000));
        assertThrows(DuplicateException.class, () -> employmentService.createEmployment(request, 1L));
    }

    @Test
    void 정상_employment_생성() {
        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));
        when(workerRepository.findById(2L)).thenReturn(Optional.of(worker));
        when(employmentRepository.existsByEmployerIdAndWorkerId(1L, 2L)).thenReturn(false);
        when(employmentRepository.save(any())).thenReturn(savedEmployment);
        when(savedEmployment.getId()).thenReturn(10L);

        CreateEmploymentRequest request = new CreateEmploymentRequest(2L, BigDecimal.valueOf(10000));
        CreateEmploymentResponse response = employmentService.createEmployment(request, 1L);

        assertEquals(10L, response.employmentId());
        assertEquals(BigDecimal.valueOf(10000), response.hourlyWage());
    }
}
