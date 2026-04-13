package com.wageclock.wageclock.domain.employment;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.global.exception.DuplicateException;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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
                .orElseThrow(() -> new NotFoundException("employer not found"));
        Worker worker = workerRepository.findById(createEmploymentRequest.workerId())
                .orElseThrow(() -> new NotFoundException("worker not found"));
        if(employmentRepository.existsByEmployerIdAndWorkerId(employerId,
                createEmploymentRequest.workerId())){
            throw new DuplicateException("employment already exists");
        }
        Employment employment = employmentRepository.save(Employment.builder()
                .employer(employer).worker(worker).hourlyWage(createEmploymentRequest.hourlyWage()).build());
        return new CreateEmploymentResponse(employment.getId(), createEmploymentRequest.hourlyWage());
    }
}
