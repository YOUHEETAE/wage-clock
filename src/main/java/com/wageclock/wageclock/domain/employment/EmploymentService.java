package com.wageclock.wageclock.domain.employment;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.global.exception.DuplicateException;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public EmploymentResponse createEmployment(EmploymentRequest employmentRequest, Long employerId){
        Employer employer = employerRepository.findById(employerId)
                .orElseThrow(() -> new NotFoundException("employer not found"));
        Worker worker = workerRepository.findById(employmentRequest.workerId())
                .orElseThrow(() -> new NotFoundException("worker not found"));
        if(employmentRepository.existsByEmployerIdAndWorkerId(employerId,
                employmentRequest.workerId())){
            throw new DuplicateException("employment already exists");
        }
        Employment employment = employmentRepository.save(Employment.builder()
                .employer(employer).worker(worker).hourlyWage(employmentRequest.hourlyWage()).build());
        return new EmploymentResponse(employment.getId(), employmentRequest.hourlyWage());
    }
}
