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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmploymentService {

    private final EmploymentRepository employmentRepository;
    private final WorkerRepository workerRepository;
    private final EmployerRepository employerRepository;
    private final WorkplaceRepository workplaceRepository;

    public EmploymentService(WorkerRepository workerRepository,
                             EmploymentRepository employmentRepository,
                             EmployerRepository employerRepository,
                             WorkplaceRepository workplaceRepository) {
        this.workerRepository = workerRepository;
        this.employmentRepository = employmentRepository;
        this.employerRepository = employerRepository;
        this.workplaceRepository = workplaceRepository;
    }

    @Transactional
    public EmploymentResponse createEmployment(EmploymentRequest employmentRequest, Long employerId) {
        Employer employer = employerRepository.findById(employerId)
                .orElseThrow(() -> new NotFoundException("employer not found"));
        Worker worker = workerRepository.findByEmail(employmentRequest.workerEmail())
                .orElseThrow(() -> new NotFoundException("worker not found"));
        Workplace workplace = workplaceRepository.findById(employmentRequest.workplaceId())
                .orElseThrow(() -> new NotFoundException("workplace not found"));
        if (!workplace.getEmployerId().equals(employerId)) {
            throw new UnauthorizedException("unauthorized");
        }
        if (employmentRepository.existsByWorkplace_IdAndWorker_Id(employmentRequest.workplaceId(), worker.getId())) {
            throw new DuplicateException("employment already exists");
        }
        Employment employment = employmentRepository.save(Employment.builder()
                .employer(employer).worker(worker).workplace(workplace)
                .hourlyWage(employmentRequest.hourlyWage()).build());
        return toResponse(employment);
    }

    private EmploymentResponse toResponse(Employment e) {
        return new EmploymentResponse(e.getId(), e.getHourlyWage(),
                e.getWorkplaceId(), e.getWorkplaceName(), e.getWorkplaceAddress());
    }
}
