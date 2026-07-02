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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
        EmploymentRequest request = new EmploymentRequest(2L, BigDecimal.valueOf(10000), "스타벅스 강남점");
        assertThrows(NotFoundException.class, () -> employmentService.createEmployment(request, 1L));
    }

    @Test
    void worker_없음_예외() {
        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));
        when(workerRepository.findById(2L)).thenReturn(Optional.empty());
        EmploymentRequest request = new EmploymentRequest(2L, BigDecimal.valueOf(10000), "스타벅스 강남점");
        assertThrows(NotFoundException.class, () -> employmentService.createEmployment(request, 1L));
    }

    @Test
    void 중복_employment_예외() {
        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));
        when(workerRepository.findById(2L)).thenReturn(Optional.of(worker));
        when(employmentRepository.existsByEmployerIdAndWorkerId(1L, 2L)).thenReturn(true);
        EmploymentRequest request = new EmploymentRequest(2L, BigDecimal.valueOf(10000), "스타벅스 강남점");
        assertThrows(DuplicateException.class, () -> employmentService.createEmployment(request, 1L));
    }

    @Test
    void 정상_employment_생성() {
        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));
        when(workerRepository.findById(2L)).thenReturn(Optional.of(worker));
        when(employmentRepository.existsByEmployerIdAndWorkerId(1L, 2L)).thenReturn(false);
        when(employmentRepository.save(any())).thenReturn(savedEmployment);
        when(savedEmployment.getId()).thenReturn(10L);
        when(savedEmployment.getHourlyWage()).thenReturn(BigDecimal.valueOf(10000));
        when(savedEmployment.getEmploymentName()).thenReturn("스타벅스 강남점");

        EmploymentRequest request = new EmploymentRequest(2L, BigDecimal.valueOf(10000), "스타벅스 강남점");
        EmploymentResponse response = employmentService.createEmployment(request, 1L);

        assertEquals(10L, response.employmentId());
        assertEquals(BigDecimal.valueOf(10000), response.hourlyWage());
        assertEquals("스타벅스 강남점", response.employmentName());
    }

    @Test
    void 내_고용_목록_반환() {
        Employment e1 = mock(Employment.class);
        Employment e2 = mock(Employment.class);
        when(e1.getId()).thenReturn(1L);
        when(e1.getHourlyWage()).thenReturn(BigDecimal.valueOf(10000));
        when(e1.getEmploymentName()).thenReturn("스타벅스 강남점");
        when(e2.getId()).thenReturn(2L);
        when(e2.getHourlyWage()).thenReturn(BigDecimal.valueOf(12000));
        when(e2.getEmploymentName()).thenReturn("맥도날드 서초점");
        when(employmentRepository.findByWorker_Id(1L)).thenReturn(List.of(e1, e2));

        List<EmploymentResponse> result = employmentService.getMyEmployments(1L);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).employmentId());
        assertEquals("스타벅스 강남점", result.get(0).employmentName());
        assertEquals(2L, result.get(1).employmentId());
        assertEquals("맥도날드 서초점", result.get(1).employmentName());
    }

    @Test
    void 고용_없으면_빈_목록_반환() {
        when(employmentRepository.findByWorker_Id(1L)).thenReturn(List.of());

        List<EmploymentResponse> result = employmentService.getMyEmployments(1L);

        assertEquals(0, result.size());
    }
}