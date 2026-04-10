package com.wageclock.wageclock.domain.employment;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.security.InvalidParameterException;

@Service
public class EmploymentService {

    private final EmploymentRepository employmentRepository;
    private final WorkerRepository workerRepository;
    private final EmployerRepository employerRepository;

    public EmploymentService(WorkerRepository workerRepository,
                             EmploymentRepository employmentRepository,
                             EmployerRepository employerRepository) {
        this.workerRepository = workerRepository;
        this.employmentRepository = employmentRepository;
        this.employerRepository = employerRepository;
    }
    public CreateEmploymentResponse createEmployment(CreateEmploymentRequest createEmploymentRequest){
        Long employerId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Employer employer = employerRepository.findById(employerId)
                .orElseThrow(() -> new InvalidParameterException("employerId"));
        Worker worker = workerRepository.findById(createEmploymentRequest.workerId())
                .orElseThrow(() -> new InvalidParameterException("workerId"));
        Employment employment = employmentRepository.save(Employment.builder()
                .employer(employer).worker(worker).hourlyWage(createEmploymentRequest.hourlyWage()).build());
        return new CreateEmploymentResponse(employment.getId(), createEmploymentRequest.hourlyWage());
    }
}
